package aster.runtime.workflow;

import java.util.List;
import java.util.Optional;

/**
 * 事件存储接口
 *
 * 提供事件溯源所需的持久化能力：
 * - 追加事件到事件流
 * - 读取事件历史
 * - 管理 workflow 状态
 * - 支持快照优化
 */
public interface EventStore {

    /**
     * 追加事件到 workflow 事件流
     *
     * 事件必须按序追加，序列号由存储层自动递增。
     * 此方法必须是原子操作，确保事件不会丢失或重复。
     *
     * @param workflowId workflow 唯一标识符
     * @param eventType 事件类型（如 WorkflowStarted, StepCompleted 等）
     * @param payload 事件负载数据
     * @return 事件序列号
     */
    default long appendEvent(String workflowId, String eventType, Object payload) {
        return appendEvent(workflowId, eventType, payload, 1, null, null);
    }

    /**
     * 追加事件到 workflow 事件流（带重试元数据）
     *
     * @param workflowId workflow 唯一标识符
     * @param eventType 事件类型（如 WorkflowStarted, StepCompleted 等）
     * @param payload 事件负载数据
     * @param attemptNumber 当前重试次数，默认 1
     * @param backoffDelayMs 退避延迟（毫秒）
     * @param failureReason 失败原因
     * @return 事件序列号
     */
    long appendEvent(String workflowId, String eventType, Object payload,
                     Integer attemptNumber, Long backoffDelayMs, String failureReason);

    /**
     * 获取 workflow 的事件历史
     *
     * 从指定序列号开始读取事件，用于状态恢复和重放。
     *
     * @param workflowId workflow 唯一标识符
     * @param fromSeq 起始序列号（包含），从 0 开始读取全部事件
     * @return 事件列表，按序列号升序排列
     */
    List<WorkflowEvent> getEvents(String workflowId, long fromSeq);

    /**
     * 获取 workflow 当前状态
     *
     * @param workflowId workflow 唯一标识符
     * @return workflow 状态，如果不存在则返回 empty
     */
    Optional<WorkflowState> getState(String workflowId);

    /**
     * 保存 workflow 状态快照
     *
     * 快照用于优化长时间运行的 workflow 的重放性能。
     * 重放时可以从最近的快照开始，而不是从事件 0 开始。
     *
     * @param workflowId workflow 唯一标识符
     * @param eventSeq 快照对应的事件序列号
     * @param state 序列化后的状态数据
     */
    void saveSnapshot(String workflowId, long eventSeq, Object state);

    /**
     * 获取最近的快照
     *
     * @param workflowId workflow 唯一标识符
     * @return 快照数据，如果不存在则返回 empty
     */
    default Optional<WorkflowSnapshot> getLatestSnapshot(String workflowId) {
        return Optional.empty();
    }
}
