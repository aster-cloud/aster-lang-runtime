package io.aster.workflow;

import aster.runtime.workflow.DeterministicClock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 可重放的确定性时钟
 *
 * 在正常执行时记录时间决策，在重放时返回记录的时间。
 * 这确保了 workflow 在重放时的行为与原始执行完全一致。
 */
public class ReplayDeterministicClock implements DeterministicClock {

    private final List<Instant> recordedTimes = new ArrayList<>();
    private int replayIndex = 0;
    private boolean replayMode = false;

    /**
     * 获取当前时间
     *
     * 在正常执行模式下，返回系统当前时间并记录；
     * 在重放模式下，返回记录的时间。
     *
     * @return 当前时间
     */
    @Override
    public Instant now() {
        if (replayMode) {
            if (replayIndex >= recordedTimes.size()) {
                throw new IllegalStateException(
                        String.format("Replay exhausted: requested time #%d but only %d times recorded",
                                replayIndex, recordedTimes.size())
                );
            }
            return recordedTimes.get(replayIndex++);
        } else {
            Instant now = Instant.now();
            recordTimeDecision(now);
            return now;
        }
    }

    /**
     * 记录时间决策
     *
     * 仅在正常执行模式下记录，重放模式下忽略。
     *
     * @param instant 要记录的时间
     */
    @Override
    public void recordTimeDecision(Instant instant) {
        if (!replayMode) {
            recordedTimes.add(instant);
        }
    }

    /**
     * 判断是否在重放模式
     *
     * @return true 如果在重放模式
     */
    @Override
    public boolean isReplayMode() {
        return replayMode;
    }

    /**
     * 进入重放模式
     *
     * 加载记录的时间序列，用于确定性重放。
     *
     * @param times 记录的时间列表
     */
    public void enterReplayMode(List<Instant> times) {
        this.recordedTimes.clear();
        this.recordedTimes.addAll(times);
        this.replayMode = true;
        this.replayIndex = 0;
    }

    /**
     * 退出重放模式
     *
     * 清空记录并恢复正常执行模式。
     */
    public void exitReplayMode() {
        this.replayMode = false;
        this.replayIndex = 0;
        this.recordedTimes.clear();
    }

    /**
     * 获取已记录的时间列表（用于持久化）
     *
     * @return 时间列表的副本
     */
    public List<Instant> getRecordedTimes() {
        return new ArrayList<>(recordedTimes);
    }
}
