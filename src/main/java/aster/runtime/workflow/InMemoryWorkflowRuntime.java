package aster.runtime.workflow;

import io.aster.workflow.DeterminismContext;
import io.aster.workflow.IdempotencyKeyManager;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存实现的 Workflow 运行时
 *
 * 用于 Phase 2.0 向后兼容和单元测试。
 * 不持久化状态，所有数据存储在内存中。
 */
public class InMemoryWorkflowRuntime implements WorkflowRuntime {

    private final Map<String, WorkflowExecutionState> executions = new ConcurrentHashMap<>();
    private final Map<String, DeterminismContext> contexts = new ConcurrentHashMap<>();
    private final InMemoryEventStore eventStore = new InMemoryEventStore();
    private final IdempotencyKeyManager idempotencyManager;
    // 用于向后兼容的默认 DeterminismContext，使用 ThreadLocal 保证线程隔离
    // DeterminismContext 非线程安全，必须每线程独立实例
    private final ThreadLocal<DeterminismContext> threadLocalContext =
            ThreadLocal.withInitial(DeterminismContext::new);

    public InMemoryWorkflowRuntime() {
        this(new IdempotencyKeyManager());
    }

    @Inject
    public InMemoryWorkflowRuntime(IdempotencyKeyManager idempotencyManager) {
        this.idempotencyManager = idempotencyManager;
    }

    /**
     * 调度 workflow 执行
     *
     * @param workflowId workflow 唯一标识符
     * @param idempotencyKey 幂等性键（可选）
     * @param metadata workflow 元数据
     * @return 执行句柄
     */
    @Override
    public ExecutionHandle schedule(String workflowId, String idempotencyKey, WorkflowMetadata metadata) {
        // 幂等性检查
        if (idempotencyKey != null) {
            Optional<String> existing = idempotencyManager.tryAcquire(
                    idempotencyKey,
                    workflowId,
                    Duration.ofHours(1)
            );
            if (existing.isPresent()) {
                String existingWorkflowId = existing.get();
                WorkflowExecutionState state = executions.get(existingWorkflowId);
                if (state != null) {
                    return state.handle;
                }
                idempotencyManager.release(idempotencyKey);
            }
        }

        // 创建执行句柄
        CompletableFuture<Object> resultFuture = new CompletableFuture<>();
        InMemoryExecutionHandle handle = new InMemoryExecutionHandle(workflowId, resultFuture);

        // 为每个 workflow 创建独立的 DeterminismContext
        DeterminismContext context = contexts.computeIfAbsent(workflowId, k -> new DeterminismContext());

        // TODO(#4): determinism 机制统一 —— 正确的绑定点。
        //   本运行时只负责"调度"（记录状态 + 追加 STARTED 事件），workflow 的
        //   实际执行 runnable 不在本仓库内（由 emitter 生成的代码 / 上层 orchestrator
        //   在线程池线程上运行）。因此 ThreadLocal 的确定性实例必须由那个执行
        //   runnable 在其自己的（执行）线程上设置与清理，而不是在这里（调度线程）。
        //   已提供安全原语：
        //       runtime.getDeterminismContext(workflowId).runWith(() -> <workflow body>);
        //   它在执行线程上 setCurrent(uuid/random)，并在 finally 中复位，从而消除
        //   "调度线程 reset、执行线程污染"的跨 workflow 串扰。
        //   旧代码在调度线程上调用 threadLocalContext.remove() 是错误的（清理的是
        //   错误的线程），已移除。一旦执行 runnable 在本仓库内可见，应在其入口
        //   处接入 context.runWith(...)。
        //   下面这行仅为编译期保留对 context 的引用，不产生副作用。
        assert context != null;

        // 记录执行状态
        WorkflowExecutionState state = new WorkflowExecutionState(handle, metadata, idempotencyKey);
        executions.put(workflowId, state);

        // 追加 WorkflowStarted 事件
        eventStore.appendEvent(workflowId, WorkflowEvent.Type.WORKFLOW_STARTED, metadata);

        return handle;
    }

    /**
     * 获取当前线程的确定性上下文（ThreadLocal 隔离）
     *
     * @return 当前线程的确定性上下文实例
     */
    public DeterminismContext getDeterminismContext() {
        return threadLocalContext.get();
    }

    /**
     * 获取指定 workflow 的确定性上下文
     *
     * @param workflowId workflow 唯一标识符
     * @return 该 workflow 对应的确定性上下文实例
     */
    public DeterminismContext getDeterminismContext(String workflowId) {
        return contexts.computeIfAbsent(workflowId, k -> new DeterminismContext());
    }

    /**
     * 获取事件存储
     *
     * @return 事件存储实例
     */
    @Override
    public EventStore getEventStore() {
        return eventStore;
    }

    /**
     * 关闭运行时
     */
    @Override
    public void shutdown() {
        executions.values().forEach(state -> {
            if (!state.handle.getResult().isDone()) {
                state.handle.cancel();
            }
        });
        executions.values().forEach(state -> {
            if (state.idempotencyKey != null) {
                idempotencyManager.release(state.idempotencyKey);
            }
        });
        executions.clear();
        contexts.clear();
        // 清理 ThreadLocal 避免内存泄漏
        threadLocalContext.remove();
    }

    /**
     * 完成 workflow 执行
     *
     * @param workflowId workflow 唯一标识符
     * @param result 执行结果
     */
    public void completeWorkflow(String workflowId, Object result) {
        // 由当前状态决定是否记录终态事件，而非依赖 executions 是否存在条目。
        // 这样即使执行状态已被先前的调用清理（例如迟到的 complete），仍能写入终态事件。
        if (isTerminal(workflowId)) {
            return;
        }
        WorkflowExecutionState state = executions.remove(workflowId);
        try {
            if (state != null) {
                state.handle.complete(result);
            }
            eventStore.appendEvent(workflowId, WorkflowEvent.Type.WORKFLOW_COMPLETED, result);
        } finally {
            releaseTerminalResources(workflowId, state);
        }
    }

    /**
     * 使 workflow 执行失败
     *
     * @param workflowId workflow 唯一标识符
     * @param error 失败原因
     */
    public void failWorkflow(String workflowId, Throwable error) {
        // 与 completeWorkflow 对称：按当前状态判断是否已是终态，保证幂等。
        if (isTerminal(workflowId)) {
            return;
        }
        WorkflowExecutionState state = executions.remove(workflowId);
        try {
            if (state != null) {
                state.handle.fail(error);
            }
            // 持久化非空的失败原因：error.getMessage() 对很多异常为 null。
            // 改为记录完整的 throwable 类名 + message 链，便于回放与排障。
            eventStore.appendEvent(workflowId, WorkflowEvent.Type.WORKFLOW_FAILED, describeThrowable(error));
        } finally {
            releaseTerminalResources(workflowId, state);
        }
    }

    /**
     * 判断 workflow 是否已处于终态（COMPLETED / FAILED），用于幂等终态转移。
     */
    private boolean isTerminal(String workflowId) {
        return eventStore.getState(workflowId)
                .map(s -> s.getStatus() == WorkflowState.Status.COMPLETED
                        || s.getStatus() == WorkflowState.Status.FAILED)
                .orElse(false);
    }

    /**
     * 释放终态相关资源（幂等键、DeterminismContext、ThreadLocal）。
     */
    private void releaseTerminalResources(String workflowId, WorkflowExecutionState state) {
        if (state != null && state.idempotencyKey != null) {
            idempotencyManager.release(state.idempotencyKey);
        }
        contexts.remove(workflowId);
        threadLocalContext.remove();
    }

    /**
     * 将异常链构造为非空的可读字符串：类名 + message，逐级追加 cause。
     */
    private static String describeThrowable(Throwable error) {
        if (error == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        Throwable t = error;
        java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (t != null && seen.add(t)) {
            if (sb.length() > 0) {
                sb.append("; caused by: ");
            }
            sb.append(t.getClass().getName());
            if (t.getMessage() != null) {
                sb.append(": ").append(t.getMessage());
            }
            t = t.getCause();
        }
        return sb.toString();
    }

    // ==================== 内部类 ====================

    /**
     * Workflow 执行状态
     */
    private static class WorkflowExecutionState {
        final InMemoryExecutionHandle handle;
        final WorkflowMetadata metadata;
        final String idempotencyKey;

        WorkflowExecutionState(InMemoryExecutionHandle handle, WorkflowMetadata metadata, String idempotencyKey) {
            this.handle = handle;
            this.metadata = metadata;
            this.idempotencyKey = idempotencyKey;
        }
    }

    /**
     * 内存执行句柄
     */
    private static class InMemoryExecutionHandle implements ExecutionHandle {
        private final String workflowId;
        private final CompletableFuture<Object> resultFuture;

        InMemoryExecutionHandle(String workflowId, CompletableFuture<Object> resultFuture) {
            this.workflowId = workflowId;
            this.resultFuture = resultFuture;
        }

        @Override
        public String getWorkflowId() {
            return workflowId;
        }

        @Override
        public CompletableFuture<Object> getResult() {
            return resultFuture;
        }

        @Override
        public void cancel() {
            resultFuture.cancel(true);
        }

        void complete(Object result) {
            resultFuture.complete(result);
        }

        void fail(Throwable error) {
            resultFuture.completeExceptionally(error);
        }
    }
}
