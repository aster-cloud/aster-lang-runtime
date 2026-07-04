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
    // 每个 workflowId 一把锁，使"检查是否终态 + 追加终态事件"成为单一原子 CAS，
    // 从而消除 complete/fail 竞态导致的 COMPLETED→FAILED 分叉。
    private final Map<String, Object> terminalLocks = new ConcurrentHashMap<>();
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
        // 创建执行句柄与执行状态
        CompletableFuture<Object> resultFuture = new CompletableFuture<>();
        InMemoryExecutionHandle handle = new InMemoryExecutionHandle(workflowId, resultFuture);
        WorkflowExecutionState state = new WorkflowExecutionState(handle, metadata, idempotencyKey);

        if (idempotencyKey != null) {
            // 关键：在幂等键“可见”之前，先把自己的执行状态登记到 executions（以自己的
            // workflowId 为键）。这样任何并发的败者在 tryAcquire 解析出 winner 的 workflowId 后，
            // executions.get(winnerWorkflowId) 必然非空，从而彻底关闭 TOCTOU 窗口——
            // 消除“败者看到 state==null → 释放 winner 的键并启动自己的 workflow”这一竞态。
            executions.put(workflowId, state);

            ExecutionHandle deduped = acquireOrDedupe(workflowId, idempotencyKey, state);
            if (deduped != null) {
                // 该幂等键已被一个存活的 workflow 占用：直接复用其句柄，绝不启动第二个。
                return deduped;
            }
            // 否则：我们赢得了该键（state 已登记），继续走下去，保证“恰好启动一次”。
        } else {
            executions.put(workflowId, state);
        }

        // 为每个 workflow 创建独立的 DeterminismContext。
        // 注意（#4，见 DeterminismContext#runWith / getDeterminismContext 的 javadoc）：
        // 本运行时只负责“调度”；实际执行 runnable 不在本仓库内，必须由执行线程调用
        // context.runWith(...) 来绑定/清理确定性实例。调度线程不得触碰这些 ThreadLocal。
        contexts.computeIfAbsent(workflowId, k -> new DeterminismContext());

        // 追加 WorkflowStarted 事件
        eventStore.appendEvent(workflowId, WorkflowEvent.Type.WORKFLOW_STARTED, metadata);

        return handle;
    }

    /**
     * 原子地获取幂等键或去重返回既有句柄。
     *
     * <p>保证：一个幂等键 ⇒ 至多一次 workflow 启动。返回 {@code null} 表示当前
     * 调用赢得了该键（调用方应继续启动 workflow）；返回非 null 表示既有存活 workflow
     * 占用该键（调用方应复用返回的句柄，不得启动）。
     *
     * @param workflowId    本次调度的 workflowId
     * @param idempotencyKey 幂等键
     * @param ownState      本次调度已登记到 executions 的状态（用于去重时回滚）
     * @return 既有句柄（去重）或 {@code null}（本次胜出）
     */
    private ExecutionHandle acquireOrDedupe(String workflowId, String idempotencyKey,
                                            WorkflowExecutionState ownState) {
        // 有界自旋：唯一会出现“键被占用但 state 为 null”的瞬态，是某个占用者已经完成、
        // 正处于 executions.remove(...) 与幂等键 release 之间的极短窗口。自旋等待其 release
        // 落地后，本次 tryAcquire 便会胜出，从而恰好启动一次；不会误启动重复 workflow。
        final int maxSpins = 10_000;
        for (int spins = 0; ; spins++) {
            Optional<String> existing = idempotencyManager.tryAcquire(
                    idempotencyKey, workflowId, Duration.ofHours(1));
            if (existing.isEmpty() || workflowId.equals(existing.get())) {
                // 我们持有该键 —— 由调用方启动 workflow。
                return null;
            }
            String holderId = existing.get();
            WorkflowExecutionState holder = executions.get(holderId);
            if (holder != null) {
                // 已有存活 workflow 占用该键：去重。回滚我们投机登记的条目，绝不释放对方的键。
                executions.remove(workflowId, ownState);
                return holder.handle;
            }
            if (spins >= maxSpins) {
                // 病态情形：键被长期占用却无存活状态。拒绝启动重复 workflow。
                executions.remove(workflowId, ownState);
                throw new IllegalStateException(
                        "Idempotency key '" + idempotencyKey + "' is held by workflow '" + holderId
                                + "' but no execution state is registered; refusing to start a duplicate workflow");
            }
            Thread.onSpinWait();
        }
    }

    /**
     * 获取当前线程的确定性上下文（ThreadLocal 隔离）。
     *
     * <p><b>警告（#4，未接线）：</b>本运行时只负责调度；workflow 的执行 runnable 不在
     * 本仓库内，尚未在其入口处调用 {@link DeterminismContext#runWith(Runnable)}。因此在
     * <em>共享线程池</em>上，静态的 {@code ReplayDeterministicRandom.current()} /
     * {@code ReplayDeterministicUuid.current()} 返回的是执行线程的 ThreadLocal 默认实例，
     * 与本方法/{@link #getDeterminismContext(String)} 返回的 per-workflow 实例脱节，可能
     * 跨 workflow 串扰。在执行 runnable 接入 {@code context.runWith(...)} 之前，静态
     * {@code current()} 在池化线程上不安全，务必改用 per-workflow 的 {@link DeterminismContext}。
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
        // 终态转移必须原子：per-workflow 锁使“检查是否终态 + 追加终态事件”不可分割，
        // 避免与并发 failWorkflow 交错造成 COMPLETED/FAILED 分叉。
        synchronized (terminalLock(workflowId)) {
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
    }

    /**
     * 使 workflow 执行失败
     *
     * @param workflowId workflow 唯一标识符
     * @param error 失败原因
     */
    public void failWorkflow(String workflowId, Throwable error) {
        // 与 completeWorkflow 对称：同一把 per-workflow 锁，使终态转移原子化。
        synchronized (terminalLock(workflowId)) {
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
    }

    /**
     * 返回指定 workflow 的终态转移锁（per-workflow 单例）。
     */
    private Object terminalLock(String workflowId) {
        return terminalLocks.computeIfAbsent(workflowId, k -> new Object());
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
        terminalLocks.remove(workflowId);
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
