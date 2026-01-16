package aster.runtime.workflow;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存实现的事件存储
 *
 * 用于 Phase 2.0 向后兼容和单元测试。
 * 不持久化事件，所有数据存储在内存中。
 */
public class InMemoryEventStore implements EventStore {

    private final Map<String, List<WorkflowEvent>> events = new ConcurrentHashMap<>();
    private final Map<String, WorkflowState> states = new ConcurrentHashMap<>();

    /**
     * 追加事件到工作流
     *
     * @param workflowId 工作流唯一标识符
     * @param eventType 事件类型
     * @param payload 事件负载
     * @return 事件序列号
     */
    @Override
    public long appendEvent(String workflowId, String eventType, Object payload) {
        return appendEvent(workflowId, eventType, payload, 1, null, null);
    }

    @Override
    public long appendEvent(String workflowId, String eventType, Object payload,
                            Integer attemptNumber, Long backoffDelayMs, String failureReason) {
        // 使用线程安全的 List 并同步追加操作
        List<WorkflowEvent> eventList = events.computeIfAbsent(workflowId,
                k -> Collections.synchronizedList(new ArrayList<>()));

        long nextSeq;

        // 同步块确保 size() + add() + updateState() 原子性
        // 避免并发时状态更新顺序错乱导致 lastEventSeq 回滚
        synchronized (eventList) {
            nextSeq = eventList.size() + 1;
            WorkflowEvent event = new WorkflowEvent(
                    nextSeq,
                    workflowId,
                    eventType,
                    payload,
                    Instant.now(),
                    attemptNumber != null ? attemptNumber : 1,
                    backoffDelayMs,
                    failureReason
            );
            eventList.add(event);
            // 状态更新必须在同步块内，确保事件序列号与状态一致
            updateState(workflowId, event);
        }

        return nextSeq;
    }

    /**
     * 获取工作流的事件历史
     *
     * @param workflowId 工作流唯一标识符
     * @param fromSeq 起始序列号（包含）
     * @return 事件列表
     */
    @Override
    public List<WorkflowEvent> getEvents(String workflowId, long fromSeq) {
        List<WorkflowEvent> eventList = events.get(workflowId);
        if (eventList == null) {
            return Collections.emptyList();
        }

        // 按 JDK 文档要求，对 synchronizedList 迭代时必须在 synchronized 块内
        List<WorkflowEvent> snapshot;
        synchronized (eventList) {
            snapshot = new ArrayList<>(eventList);
        }

        return snapshot.stream()
                .filter(e -> e.getSequence() >= fromSeq)
                .collect(Collectors.toList());
    }

    /**
     * 获取工作流当前状态
     *
     * @param workflowId 工作流唯一标识符
     * @return 工作流状态
     */
    @Override
    public Optional<WorkflowState> getState(String workflowId) {
        return Optional.ofNullable(states.get(workflowId));
    }

    /**
     * 保存快照
     *
     * @param workflowId 工作流唯一标识符
     * @param eventSeq 快照对应的事件序列号
     * @param state 序列化后的状态数据
     */
    @Override
    public void saveSnapshot(String workflowId, long eventSeq, Object state) {
        // 获取事件列表作为同步锁，与 appendEvent/updateState 使用相同锁
        List<WorkflowEvent> eventList = events.get(workflowId);
        if (eventList == null) {
            return;
        }

        synchronized (eventList) {
            // 在同步块内重新读取最新状态，避免竞态覆盖
            WorkflowState currentState = states.get(workflowId);
            if (currentState != null) {
                WorkflowState newState = new WorkflowState(
                        currentState.getWorkflowId(),
                        currentState.getStatus(),
                        currentState.getLastEventSeq(),
                        currentState.getResult(),
                        state,
                        eventSeq,
                        currentState.getCreatedAt(),
                        Instant.now()
                );
                states.put(workflowId, newState);
            }
        }
    }

    /**
     * 获取最近的快照
     *
     * @param workflowId 工作流唯一标识符
     * @return 快照数据
     */
    @Override
    public Optional<WorkflowSnapshot> getLatestSnapshot(String workflowId) {
        WorkflowState state = states.get(workflowId);
        if (state != null && state.getSnapshot() != null) {
            return Optional.of(new WorkflowSnapshot(
                    workflowId,
                    state.getSnapshotSeq(),
                    state.getSnapshot()
            ));
        }
        return Optional.empty();
    }

    /**
     * 清空所有数据（用于测试）
     */
    public void clear() {
        events.clear();
        states.clear();
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 根据事件更新工作流状态
     */
    private void updateState(String workflowId, WorkflowEvent event) {
        WorkflowState currentState = states.get(workflowId);
        Instant now = Instant.now();

        WorkflowState.Status newStatus;
        Object newResult = null;

        switch (event.getEventType()) {
            case WorkflowEvent.Type.WORKFLOW_STARTED:
                newStatus = WorkflowState.Status.READY;
                break;
            case WorkflowEvent.Type.WORKFLOW_COMPLETED:
                newStatus = WorkflowState.Status.COMPLETED;
                newResult = event.getPayload();
                break;
            case WorkflowEvent.Type.WORKFLOW_FAILED:
                newStatus = WorkflowState.Status.FAILED;
                newResult = event.getPayload();
                break;
            case WorkflowEvent.Type.COMPENSATION_SCHEDULED:
                newStatus = WorkflowState.Status.COMPENSATING;
                break;
            case WorkflowEvent.Type.COMPENSATION_COMPLETED:
                newStatus = WorkflowState.Status.COMPENSATED;
                break;
            case WorkflowEvent.Type.COMPENSATION_FAILED:
                newStatus = WorkflowState.Status.COMPENSATION_FAILED;
                break;
            default:
                newStatus = currentState != null ? currentState.getStatus() : WorkflowState.Status.READY;
                break;
        }

        if (currentState == null) {
            WorkflowState newState = new WorkflowState(
                    workflowId,
                    newStatus,
                    event.getSequence(),
                    newResult,
                    null,
                    0L,
                    now,
                    now
            );
            states.put(workflowId, newState);
        } else {
            WorkflowState newState = new WorkflowState(
                    workflowId,
                    newStatus,
                    event.getSequence(),
                    newResult != null ? newResult : currentState.getResult(),
                    currentState.getSnapshot(),
                    currentState.getSnapshotSeq(),
                    currentState.getCreatedAt(),
                    now
            );
            states.put(workflowId, newState);
        }
    }
}
