package io.aster.workflow;

import java.time.Instant;

/**
 * 内存实现的确定性时钟（test-only）
 *
 * <p>仅用于单元测试：这是一个 no-op 实现，不支持重放模式，直接返回系统时间，
 * 因此不具备生产用途。为打破 {@code aster.runtime.workflow} 与
 * {@code io.aster.workflow} 之间的包循环并避免在主源集中暴露非确定性实现，
 * 该类已随 {@link DeterministicClock} 一同迁入 {@code io.aster.workflow}，
 * 并放置在 test 源集中（issue #6）。
 *
 * 不支持重放模式，直接返回系统时间。
 */
public class InMemoryDeterministicClock implements DeterministicClock {

    /**
     * 获取当前时间
     *
     * @return 当前时间戳
     */
    @Override
    public Instant now() {
        Instant currentTime = Instant.now();
        recordTimeDecision(currentTime);
        return currentTime;
    }

    /**
     * 记录时间决策
     *
     * 内存实现不需要记录时间，仅返回系统时间。
     *
     * @param instant 时间戳
     */
    @Override
    public void recordTimeDecision(Instant instant) {
        // 内存实现不需要记录时间决策
    }
}
