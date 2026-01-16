package aster.runtime.workflow;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Workflow 事件
 *
 * 表示 workflow 执行过程中的一个事件，包含事件类型、负载数据和元数据。
 */
public class WorkflowEvent {

    private final long sequence;
    private final String workflowId;
    private final String eventType;
    private final Object payload;
    private final Instant occurredAt;
    private final Integer attemptNumber;
    private final Long backoffDelayMs;
    private final String failureReason;

    /**
     * 构造 workflow 事件
     *
     * @param sequence 事件序列号
     * @param workflowId workflow 唯一标识符
     * @param eventType 事件类型
     * @param payload 事件负载数据
     * @param occurredAt 事件发生时间
     */
    public WorkflowEvent(long sequence, String workflowId, String eventType, Object payload, Instant occurredAt) {
        this(sequence, workflowId, eventType, payload, occurredAt, 1, null, null);
    }

    /**
     * 构造支持重试元数据的 workflow 事件
     */
    public WorkflowEvent(long sequence, String workflowId, String eventType, Object payload, Instant occurredAt,
                         Integer attemptNumber, Long backoffDelayMs, String failureReason) {
        this.sequence = sequence;
        this.workflowId = workflowId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.attemptNumber = attemptNumber != null ? attemptNumber : 1;
        this.backoffDelayMs = backoffDelayMs;
        this.failureReason = failureReason;
    }

    /**
     * 获取事件序列号
     *
     * 序列号在同一 workflow 内单调递增，从 1 开始。
     *
     * @return 事件序列号
     */
    public long getSequence() {
        return sequence;
    }

    /**
     * 获取 workflow ID
     *
     * @return workflow 唯一标识符
     */
    public String getWorkflowId() {
        return workflowId;
    }

    /**
     * 获取事件类型
     *
     * 常见事件类型：
     * - WorkflowStarted: workflow 开始执行
     * - StepScheduled: step 被调度
     * - StepStarted: step 开始执行
     * - StepCompleted: step 执行成功
     * - StepFailed: step 执行失败
     * - CompensationScheduled: 补偿被调度
     * - CompensationCompleted: 补偿执行完成
     * - TimerScheduled: 定时器被调度
     * - TimerFired: 定时器触发
     * - WorkflowCompleted: workflow 执行完成
     * - WorkflowFailed: workflow 执行失败
     *
     * @return 事件类型
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * 获取事件负载数据
     *
     * 负载包含事件相关的具体数据，格式取决于事件类型。
     *
     * @return 事件负载
     */
    public Object getPayload() {
        return payload;
    }

    /**
     * 获取事件发生时间
     *
     * @return 事件时间戳
     */
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * 获取当前重试次数，默认 1
     */
    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    /**
     * 获取退避时间（毫秒）
     */
    public Long getBackoffDelayMs() {
        return backoffDelayMs;
    }

    /**
     * 获取失败原因
     */
    public String getFailureReason() {
        return failureReason;
    }

    @Override
    public String toString() {
        return String.format("WorkflowEvent{seq=%d, workflowId='%s', type='%s', attempt=%d, occurredAt=%s}",
                sequence, workflowId, eventType, attemptNumber, occurredAt);
    }

    /**
     * 创建 StepStarted 事件
     *
     * 统一标准 payload，确保包含 stepId、dependencies、status 与 startedAt，便于审计与重放。
     */
    public static WorkflowEvent stepStarted(String workflowId, String stepId, List<String> dependencies) {
        Instant now = Instant.now();
        Map<String, Object> payload = new HashMap<>();
        payload.put("stepId", stepId);
        payload.put("dependencies", normalizeDependencies(dependencies));
        payload.put("status", "STARTED");
        payload.put("startedAt", now.toString());

        return new WorkflowEvent(0L, workflowId, Type.STEP_STARTED, payload, now);
    }

    /**
     * 创建 StepCompleted 事件
     *
     * 记录步骤完成时间、依赖与结果，便于重放 DAG。
     */
    public static WorkflowEvent stepCompleted(String workflowId, String stepId, List<String> dependencies, Object result) {
        Instant now = Instant.now();
        Map<String, Object> payload = new HashMap<>();
        payload.put("stepId", stepId);
        payload.put("dependencies", normalizeDependencies(dependencies));
        payload.put("status", "COMPLETED");
        payload.put("completedAt", now.toString());
        if (result != null) {
            payload.put("result", result);
        }

        return new WorkflowEvent(0L, workflowId, Type.STEP_COMPLETED, payload, now);
    }

    /**
     * 创建 StepFailed 事件
     *
     * 记录失败原因与依赖，便于补偿与审计。
     */
    public static WorkflowEvent stepFailed(String workflowId, String stepId, List<String> dependencies, String error) {
        Instant now = Instant.now();
        Map<String, Object> payload = new HashMap<>();
        payload.put("stepId", stepId);
        payload.put("dependencies", normalizeDependencies(dependencies));
        payload.put("status", "FAILED");
        payload.put("completedAt", now.toString());
        if (error != null && !error.isEmpty()) {
            payload.put("error", error);
        }

        return new WorkflowEvent(0L, workflowId, Type.STEP_FAILED, payload, now);
    }

    private static List<String> normalizeDependencies(List<String> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(dependencies);
    }

    /**
     * 事件类型常量
     */
    public static final class Type {
        private Type() {} // 禁止实例化

        /** Workflow 开始执行 */
        public static final String WORKFLOW_STARTED = "WorkflowStarted";
        /** Step 被调度 */
        public static final String STEP_SCHEDULED = "StepScheduled";
        /** Step 开始执行 */
        public static final String STEP_STARTED = "StepStarted";
        /** Step 执行成功 */
        public static final String STEP_COMPLETED = "StepCompleted";
        /** Step 执行失败 */
        public static final String STEP_FAILED = "StepFailed";
        /** 补偿被调度 */
        public static final String COMPENSATION_SCHEDULED = "CompensationScheduled";
        /** 补偿开始执行 */
        public static final String COMPENSATION_STARTED = "CompensationStarted";
        /** 补偿执行完成 */
        public static final String COMPENSATION_COMPLETED = "CompensationCompleted";
        /** 补偿执行失败 */
        public static final String COMPENSATION_FAILED = "CompensationFailed";
        /** 定时器被调度 */
        public static final String TIMER_SCHEDULED = "TimerScheduled";
        /** 定时器触发 */
        public static final String TIMER_FIRED = "TimerFired";
        /** Workflow 执行完成 */
        public static final String WORKFLOW_COMPLETED = "WorkflowCompleted";
        /** Workflow 执行失败 */
        public static final String WORKFLOW_FAILED = "WorkflowFailed";
        /** Workflow 已终止 */
        public static final String WORKFLOW_TERMINATED = "WorkflowTerminated";
    }
}
