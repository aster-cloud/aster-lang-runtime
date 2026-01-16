package aster.runtime.workflow;

/**
 * Workflow 快照
 *
 * 表示 workflow 在某个事件序列号时的状态快照，用于优化重放性能。
 */
public class WorkflowSnapshot {

    private final String workflowId;
    private final long eventSeq;
    private final Object state;

    /**
     * 构造 workflow 快照
     *
     * @param workflowId workflow 唯一标识符
     * @param eventSeq 快照对应的事件序列号
     * @param state 序列化后的状态数据
     */
    public WorkflowSnapshot(String workflowId, long eventSeq, Object state) {
        this.workflowId = workflowId;
        this.eventSeq = eventSeq;
        this.state = state;
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
     * 获取快照对应的事件序列号
     *
     * 重放时可以从此序列号之后的事件开始，而不是从事件 0 开始。
     *
     * @return 事件序列号
     */
    public long getEventSeq() {
        return eventSeq;
    }

    /**
     * 获取序列化后的状态数据
     *
     * 状态数据的格式取决于具体实现，通常是 JSON 或二进制序列化格式。
     *
     * @return 状态数据
     */
    public Object getState() {
        return state;
    }

    @Override
    public String toString() {
        return String.format("WorkflowSnapshot{workflowId='%s', eventSeq=%d}",
                workflowId, eventSeq);
    }
}
