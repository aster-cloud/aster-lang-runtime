package aster.runtime.workflow;

import java.time.Instant;

/**
 * Workflow 状态
 *
 * 表示 workflow 的当前执行状态，包括状态类型、最后处理的事件序列号、结果等。
 */
public class WorkflowState {

    private final String workflowId;
    private final Status status;
    private final long lastEventSeq;
    private final Object result;
    private final Object snapshot;
    private final Long snapshotSeq;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * 构造 workflow 状态
     *
     * @param workflowId workflow 唯一标识符
     * @param status 状态类型
     * @param lastEventSeq 最后处理的事件序列号
     * @param result 执行结果（仅在 COMPLETED 或 FAILED 状态时有值）
     * @param snapshot 快照数据
     * @param snapshotSeq 快照对应的事件序列号
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public WorkflowState(
            String workflowId,
            Status status,
            long lastEventSeq,
            Object result,
            Object snapshot,
            Long snapshotSeq,
            Instant createdAt,
            Instant updatedAt) {
        this.workflowId = workflowId;
        this.status = status;
        this.lastEventSeq = lastEventSeq;
        this.result = result;
        this.snapshot = snapshot;
        this.snapshotSeq = snapshotSeq;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 获取 workflow 唯一标识符
     * @return workflow 唯一标识符
     */
    public String getWorkflowId() {
        return workflowId;
    }

    /**
     * 获取状态类型
     * @return 状态类型
     */
    public Status getStatus() {
        return status;
    }

    /**
     * 获取最后处理的事件序列号
     * @return 最后处理的事件序列号
     */
    public long getLastEventSeq() {
        return lastEventSeq;
    }

    /**
     * 获取执行结果
     * @return 执行结果（仅在 COMPLETED 或 FAILED 状态时有值）
     */
    public Object getResult() {
        return result;
    }

    /**
     * 获取快照数据
     * @return 快照数据
     */
    public Object getSnapshot() {
        return snapshot;
    }

    /**
     * 获取快照对应的事件序列号
     * @return 快照对应的事件序列号
     */
    public Long getSnapshotSeq() {
        return snapshotSeq;
    }

    /**
     * 获取创建时间
     * @return 创建时间
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 获取更新时间
     * @return 更新时间
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return String.format("WorkflowState{workflowId='%s', status=%s, lastEventSeq=%d}",
                workflowId, status, lastEventSeq);
    }

    /**
     * Workflow 状态枚举
     */
    public enum Status {
        /** 就绪状态，等待调度 */
        READY,

        /** 运行中 */
        RUNNING,

        /** 已完成 */
        COMPLETED,

        /** 执行失败 */
        FAILED,

        /** 补偿中 */
        COMPENSATING,

        /** 补偿完成 */
        COMPENSATED,

        /** 补偿失败（需要人工介入） */
        COMPENSATION_FAILED,

        /** 已终止 */
        TERMINATED
    }
}
