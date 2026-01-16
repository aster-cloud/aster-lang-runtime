package aster.runtime.workflow;

import java.time.Instant;

/**
 * 内存实现的确定性时钟
 *
 * 用于 Phase 2.0 向后兼容和单元测试。
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
