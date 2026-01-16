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
}
