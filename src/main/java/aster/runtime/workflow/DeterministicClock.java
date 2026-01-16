package aster.runtime.workflow;

import java.time.Instant;

/**
 * 确定性时钟接口
 *
 * 提供时间获取能力，支持两种模式：
 * 1. 执行模式：返回真实时间并记录时间决策
 * 2. 重放模式：返回之前记录的时间，确保重放时的确定性
 *
 * 这是实现 workflow 事件溯源和确定性重放的关键组件。
 * 符合 DESIGN.md:206-214 的 determinism contract。
 */
public interface DeterministicClock {

    /**
     * 获取当前时间
     *
     * 在执行模式下，返回真实的当前时间并通过 {@link #recordTimeDecision} 记录。
     * 在重放模式下，返回之前记录的时间值，确保每次重放得到相同结果。
     *
     * @return 当前时间（执行模式）或记录的时间（重放模式）
     */
    Instant now();

    /**
     * 记录时间决策
     *
     * 仅在执行模式下调用，用于记录时间相关的决策，以便后续重放。
     * 在重放模式下，此方法应该被忽略或抛出异常。
     *
     * @param instant 要记录的时间值
     */
    void recordTimeDecision(Instant instant);

    /**
     * 检查当前是否处于重放模式
     *
     * @return true 如果处于重放模式，false 如果处于执行模式
     */
    default boolean isReplayMode() {
        return false;
    }
}
