package aster.runtime.workflow;

/**
 * WorkflowRuntime SPI 接口
 *
 * 为 Truffle WorkflowNode 与持久化运行时之间建立抽象边界，支持内存和 PostgreSQL 两种实现。
 *
 * 主要职责：
 * - 调度 workflow 执行并提供幂等性保证
 * - 提供确定性时钟以支持事件重放
 * - 提供事件存储访问接口
 * - 管理运行时生命周期
 */
public interface WorkflowRuntime {

    /**
     * 调度 workflow 执行
     *
     * @param workflowId workflow 唯一标识符
     * @param idempotencyKey 幂等性键，用于防止重复执行。如果为 null 则不进行幂等性检查。
     * @param metadata workflow 元数据，包含重试策略、超时配置等
     * @return 执行句柄，可用于获取执行结果或取消执行
     */
    ExecutionHandle schedule(String workflowId, String idempotencyKey, WorkflowMetadata metadata);

    /**
     * 获取确定性时钟实例
     *
     * 确定性时钟在初始执行时返回真实时间并记录，在重放模式下返回记录的时间，
     * 确保 workflow 在重放时具有确定性行为。
     *
     * @return 确定性时钟实例
     */
    DeterministicClock getClock();

    /**
     * 获取事件存储实例
     *
     * 事件存储用于持久化 workflow 执行事件，支持事件溯源和状态恢复。
     *
     * @return 事件存储实例
     */
    EventStore getEventStore();

    /**
     * 关闭运行时并释放资源
     *
     * 此方法应等待所有进行中的 workflow 执行完成或超时后再返回。
     */
    void shutdown();
}
