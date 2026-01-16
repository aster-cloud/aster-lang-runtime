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
        contexts.computeIfAbsent(workflowId, k -> new DeterminismContext());

        // 重置 ThreadLocal 确保废弃接口获取的是新 context（避免跨 workflow 污染）
        threadLocalContext.remove();

        // 记录执行状态
        WorkflowExecutionState state = new WorkflowExecutionState(handle, metadata, idempotencyKey);
        executions.put(workflowId, state);

        // 追加 WorkflowStarted 事件
        eventStore.appendEvent(workflowId, WorkflowEvent.Type.WORKFLOW_STARTED, metadata);

        return handle;
    }

    /**
     * 获取确定性上下文（Phase 0 Task 1.5）
     *
     * 注意：此方法已废弃，返回当前线程的 context 实例（ThreadLocal 隔离）。
     * 建议使用 getDeterminismContext(workflowId) 获取特定 workflow 的 context。
     *
     * @return 当前线程的确定性上下文实例
     */
    @Deprecated
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
     * 兼容旧接口：返回 DeterminismContext 内部的时钟
     *
     * 注意：此方法已废弃。由于每个 workflow 现在有独立的 DeterminismContext，
     * 此方法返回当前线程 context 的时钟实例（ThreadLocal 隔离）。
     * 建议使用 getDeterminismContext(workflowId).clock() 获取特定 workflow 的时钟。
     *
     * @return 当前线程 context 的时钟实例
     */
    @Deprecated
    @Override
    public DeterministicClock getClock() {
        return threadLocalContext.get().clock();
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
        // 使用 remove() 而非 get()，避免内存泄漏和幂等污染
        WorkflowExecutionState state = executions.remove(workflowId);
        if (state != null) {
            try {
                state.handle.complete(result);
                eventStore.appendEvent(workflowId, WorkflowEvent.Type.WORKFLOW_COMPLETED, result);
            } finally {
                // 确保即使 appendEvent 抛异常，资源也能正确释放
                if (state.idempotencyKey != null) {
                    idempotencyManager.release(state.idempotencyKey);
                }
                // 清理 DeterminismContext 避免内存泄漏
                contexts.remove(workflowId);
                // 清理 ThreadLocal 避免跨 workflow 污染
                threadLocalContext.remove();
            }
        }
    }

    /**
     * 使 workflow 执行失败
     *
     * @param workflowId workflow 唯一标识符
     * @param error 失败原因
     */
    public void failWorkflow(String workflowId, Throwable error) {
        // 使用 remove() 而非 get()，避免内存泄漏和幂等污染
        WorkflowExecutionState state = executions.remove(workflowId);
        if (state != null) {
            try {
                state.handle.fail(error);
                eventStore.appendEvent(workflowId, WorkflowEvent.Type.WORKFLOW_FAILED, error.getMessage());
            } finally {
                // 确保即使 appendEvent 抛异常，资源也能正确释放
                if (state.idempotencyKey != null) {
                    idempotencyManager.release(state.idempotencyKey);
                }
                // 清理 DeterminismContext 避免内存泄漏
                contexts.remove(workflowId);
                // 清理 ThreadLocal 避免跨 workflow 污染
                threadLocalContext.remove();
            }
        }
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
