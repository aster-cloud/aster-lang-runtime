package io.aster.workflow;

/**
 * 确定性上下文，封装 clock/uuid/random 三个 facade
 * 用于 ThreadLocal 隔离，确保每个 workflow 独立的确定性环境
 */
public class DeterminismContext {
    private final ReplayDeterministicClock clock;
    private final ReplayDeterministicUuid uuid;
    private final ReplayDeterministicRandom random;

    public DeterminismContext() {
        this.clock = new ReplayDeterministicClock();
        this.uuid = new ReplayDeterministicUuid();
        this.random = new ReplayDeterministicRandom();
    }

    public ReplayDeterministicClock clock() {
        return clock;
    }

    public ReplayDeterministicUuid uuid() {
        return uuid;
    }

    public ReplayDeterministicRandom random() {
        return random;
    }

    /**
     * 在当前线程上绑定本上下文持有的确定性实例，运行给定动作，结束后在
     * <b>同一线程</b>的 {@code finally} 中清理 ThreadLocal。
     *
     * <p>这是 issue #4 关于"确定性机制统一"的核心原语：workflow 的执行
     * runnable 必须在真正执行它的（线程池）线程上调用本方法，使得执行体内
     * 通过 {@link ReplayDeterministicUuid#current()} /
     * {@link ReplayDeterministicRandom#current()} 拿到的，正是本 workflow 对应
     * 的 per-workflow 实例，而不是 ThreadLocal 默认 new 出的脱节实例。绑定与
     * 清理都发生在执行线程上，从而消除"在调度线程上 reset、在执行线程上污染"
     * 的跨 workflow 串扰。
     *
     * <p>注意：{@link ReplayDeterministicClock} 没有 ThreadLocal facade，执行体
     * 应直接通过 {@link #clock()} 访问本上下文的时钟实例。
     *
     * @param action 需要在绑定上下文中运行的动作
     */
    public void runWith(Runnable action) {
        // 先记录调用前的绑定，结束后精确恢复（而不是无条件 remove），
        // 以支持嵌套/复用线程的安全性。
        ReplayDeterministicUuid prevUuid = ReplayDeterministicUuid.current();
        ReplayDeterministicRandom prevRandom = ReplayDeterministicRandom.current();
        ReplayDeterministicUuid.setCurrent(uuid);
        ReplayDeterministicRandom.setCurrent(random);
        try {
            action.run();
        } finally {
            // 在执行线程上清理，避免线程复用导致的跨 workflow 污染。
            ReplayDeterministicUuid.setCurrent(prevUuid);
            ReplayDeterministicRandom.setCurrent(prevRandom);
        }
    }
}
