package io.aster.workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 可重放且具备 source 颗粒度的确定性随机数门面。
 *
 * 该实现与 ReplayDeterministicUuid/Clock 共享状态机语义：
 * - 记录模式：调用 ThreadLocalRandom 生成值并以 source 为键进行录制；
 * - 重放模式：按照录制序列返回固定值，耗尽后立即抛出异常。
 */
public final class ReplayDeterministicRandom {

    /**
     * 单个 source 最多允许的记录条数。
     */
    public static final int MAX_RECORDS_PER_SOURCE = 500;

    private static final Logger LOG = Logger.getLogger(ReplayDeterministicRandom.class.getName());

    /**
     * 使用 ThreadLocal 隔离实例，保证跨 workflow 的决策互不污染。
     */
    private static final ThreadLocal<ReplayDeterministicRandom> THREAD_LOCAL =
            ThreadLocal.withInitial(ReplayDeterministicRandom::new);

    private final Map<String, List<Long>> recorded = new LinkedHashMap<>();
    private final Map<String, Integer> replayIndices = new HashMap<>();
    private final Set<String> limitWarnedSources = new HashSet<>();
    private boolean replayMode = false;

    /**
     * 获取当前线程绑定的随机门面。
     *
     * @return 当前线程实例
     */
    public static ReplayDeterministicRandom current() {
        return THREAD_LOCAL.get();
    }

    /**
     * 清理线程绑定实例，供线程复用场景显式释放。
     */
    public static void clearCurrent() {
        THREAD_LOCAL.remove();
    }

    /**
     * 生成 int，并复用 long 的录制/重放语义。
     *
     * @param source 调用来源标识
     * @return 确定性的 int
     */
    public int nextInt(String source) {
        return (int) nextLong(source);
    }

    /**
     * 生成 long：记录模式写入 recorded，重放模式从 recorded 读取。
     *
     * @param source 调用来源标识
     * @return 确定性的 long
     */
    public long nextLong(String source) {
        Objects.requireNonNull(source, "source");

        if (replayMode) {
            return replayValue(source);
        }

        long value = ThreadLocalRandom.current().nextLong();
        appendRecordedValue(source, value);
        return value;
    }

    /**
     * 生成 double，直接复用 long bit-pattern，保证重放一致性。
     *
     * @param source 调用来源标识
     * @return 确定性的 double
     */
    public double nextDouble(String source) {
        long bits = nextLong(source);
        return Double.longBitsToDouble(bits);
    }

    /**
     * 判断当前是否处于重放模式。
     *
     * @return true 表示正在重放
     */
    public boolean isReplayMode() {
        return replayMode;
    }

    /**
     * 进入重放模式，加载外部持久化的随机序列。
     *
     * @param randoms source→随机序列
     */
    public void enterReplayMode(Map<String, List<Long>> randoms) {
        Objects.requireNonNull(randoms, "randoms");
        resetStateForReplay();

        randoms.forEach((source, values) -> {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(values, "values");

            List<Long> target = new ArrayList<>();
            int limit = Math.min(values.size(), MAX_RECORDS_PER_SOURCE);
            for (int i = 0; i < limit; i++) {
                Long value = values.get(i);
                Objects.requireNonNull(value, "recorded value");
                target.add(value);
            }
            recorded.put(source, target);

            if (values.size() > MAX_RECORDS_PER_SOURCE) {
                LOG.log(Level.WARNING,
                        String.format("随机源 %s 的重放序列超过上限 %d，已截断", source, MAX_RECORDS_PER_SOURCE));
                limitWarnedSources.add(source);
            }
        });

        this.replayMode = true;
    }

    /**
     * 退出重放模式并清空旧的序列，以便重新录制。
     */
    public void exitReplayMode() {
        this.replayMode = false;
        this.replayIndices.clear();
        this.limitWarnedSources.clear();
        this.recorded.clear();
    }

    /**
     * 获取已录制随机序列的防御性拷贝。
     *
     * @return source→随机序列
     */
    public Map<String, List<Long>> getRecordedRandoms() {
        Map<String, List<Long>> copy = new LinkedHashMap<>();
        recorded.forEach((source, values) -> copy.put(source, new ArrayList<>(values)));
        return copy;
    }

    /**
     * 将新生成的随机值附加到 recorded，并执行上限与告警逻辑。
     */
    private void appendRecordedValue(String source, long value) {
        List<Long> values = recorded.computeIfAbsent(source, k -> new ArrayList<>(MAX_RECORDS_PER_SOURCE));
        if (values.size() >= MAX_RECORDS_PER_SOURCE) {
            emitLimitWarningOnce(source);
            return;
        }
        values.add(value);
    }

    /**
     * 根据 replayIndices 返回对应 source 的下一个重放值。
     */
    private long replayValue(String source) {
        List<Long> values = recorded.get(source);
        if (values == null) {
            throw new IllegalStateException("No recorded values for source: " + source);
        }

        int idx = replayIndices.getOrDefault(source, 0);
        if (idx >= values.size()) {
            throw new IllegalStateException(
                    String.format(
                            "Random replay exhausted for source '%s': requested #%d but only %d recorded",
                            source, idx, values.size()
                    )
            );
        }

        replayIndices.put(source, idx + 1);
        return values.get(idx);
    }

    /**
     * 限制 warning 仅出现一次，避免刷屏。
     */
    private void emitLimitWarningOnce(String source) {
        if (limitWarnedSources.add(source)) {
            LOG.log(Level.WARNING,
                    String.format("随机源 %s 的记录数已达到上限 %d，后续值将被丢弃", source, MAX_RECORDS_PER_SOURCE));
        }
    }

    /**
     * 重放前重置状态，确保 recorded 仅包含外部传入的序列。
     */
    private void resetStateForReplay() {
        this.recorded.clear();
        this.replayIndices.clear();
        this.limitWarnedSources.clear();
        this.replayMode = false;
    }
}
