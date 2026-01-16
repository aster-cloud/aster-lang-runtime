package aster.runtime.workflow;

import java.util.concurrent.CompletableFuture;

/**
 * Workflow 执行句柄
 *
 * 表示一个正在进行或已完成的 workflow 执行实例，提供查询状态和获取结果的能力。
 */
public interface ExecutionHandle {

    /**
     * 获取 workflow 唯一标识符
     *
     * @return workflow ID
     */
    String getWorkflowId();

    /**
     * 获取 workflow 执行结果
     *
     * 返回一个 CompletableFuture，当 workflow 执行完成时完成。
     * 如果 workflow 失败，future 将以异常完成。
     *
     * @return 包含执行结果的 CompletableFuture
     */
    CompletableFuture<Object> getResult();

    /**
     * 取消 workflow 执行
     *
     * 尝试取消正在执行的 workflow。如果 workflow 已经完成或失败，此方法无效。
     * 取消操作是尽力而为的，不保证一定成功。
     */
    void cancel();
}
