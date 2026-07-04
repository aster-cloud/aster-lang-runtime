package io.aster.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 可重放的确定性 UUID 生成器，完全复用 ReplayDeterministicClock 的状态机语义。
 */
public final class ReplayDeterministicUuid {

    /**
     * UUID 记录最大容量，超过后仅保留前 500 条并给出警告。
     */
    public static final int MAX_RECORDS = 500;

    private static final Logger LOG = Logger.getLogger(ReplayDeterministicUuid.class.getName());

    /**
     * 使用 ThreadLocal 确保每个 workflow 拥有独立实例，避免跨线程污染。
     */
    private static final ThreadLocal<ReplayDeterministicUuid> THREAD_LOCAL =
            ThreadLocal.withInitial(ReplayDeterministicUuid::new);

    private final List<UUID> recorded = new ArrayList<>(MAX_RECORDS);
    private int replayIndex = 0;
    private boolean replayMode = false;
    private boolean recordLimitReached = false;

    /**
     * 获取当前线程绑定的确定性 UUID 生成器。
     *
     * @return 当前线程实例
     */
    public static ReplayDeterministicUuid current() {
        // 警告（#4，未接线）：在共享线程池上，若执行 runnable 未通过
        // DeterminismContext#runWith(...) 绑定 per-workflow 实例，本方法返回的是执行线程
        // 的 ThreadLocal 默认实例，可能与调度侧的 per-workflow 实例脱节并跨 workflow 串扰。
        // 在接线完成前，池化线程上使用静态 current() 不安全。
        return THREAD_LOCAL.get();
    }

    /**
     * 将给定实例绑定到当前线程。
     *
     * <p>用于把某个 workflow 的 {@link DeterminismContext} 拥有的实例绑定到
     * 实际执行该 workflow 的（线程池）线程上。必须在执行线程上的
     * {@code finally} 中调用 {@link #clearCurrent()} 复位。
     *
     * @param instance 要绑定的实例
     */
    public static void setCurrent(ReplayDeterministicUuid instance) {
        Objects.requireNonNull(instance, "instance");
        THREAD_LOCAL.set(instance);
    }

    /**
     * 清理当前线程绑定的实例，避免线程复用导致状态泄漏。
     */
    public static void clearCurrent() {
        THREAD_LOCAL.remove();
    }

    /**
     * 生成 UUID。
     *
     * 记录模式：生成新的 UUID 并记录；
     * 重放模式：按照 recorded 序列依次返回，耗尽后抛出异常。
     *
     * @return 确定性的 UUID
     */
    public UUID randomUUID() {
        if (replayMode) {
            if (replayIndex >= recorded.size()) {
                throw new IllegalStateException(
                        String.format(
                                "UUID replay exhausted: requested UUID #%d but only %d recorded",
                                replayIndex, recorded.size()
                        )
                );
            }
            return recorded.get(replayIndex++);
        }

        UUID uuid = generateUuid();
        appendRecordedUuid(uuid);
        return uuid;
    }

    /**
     * 当前是否处于重放模式。
     *
     * @return true 表示处于重放模式
     */
    public boolean isReplayMode() {
        return replayMode;
    }

    /**
     * 进入重放模式，加载持久化的 UUID 决策序列。
     *
     * @param uuids 记录的 UUID 序列
     */
    public void enterReplayMode(List<UUID> uuids) {
        Objects.requireNonNull(uuids, "uuids");
        resetRecordingState();

        if (uuids.size() > MAX_RECORDS) {
            LOG.log(Level.WARNING,
                    String.format("UUID 重放序列超过上限 %d，已截断至前 %d 条", MAX_RECORDS, MAX_RECORDS));
        }

        for (UUID uuid : uuids) {
            Objects.requireNonNull(uuid, "记录的 UUID 不允许为 null");
            if (recorded.size() >= MAX_RECORDS) {
                break;
            }
            recorded.add(uuid);
        }

        this.replayMode = true;
        this.replayIndex = 0;
        this.recordLimitReached = recorded.size() >= MAX_RECORDS && uuids.size() > MAX_RECORDS;
    }

    /**
     * 退出重放模式，恢复记录模式并清空旧的决策。
     */
    public void exitReplayMode() {
        this.replayMode = false;
        this.replayIndex = 0;
        this.recorded.clear();
        this.recordLimitReached = false;
    }

    /**
     * 获取防御性拷贝，便于持久化。
     *
     * @return recorded 的副本
     */
    public List<UUID> getRecordedUuids() {
        return new ArrayList<>(recorded);
    }

    /**
     * 本次录制/重放是否因触达 {@link #MAX_RECORDS} 上限而发生截断（因而记录不再完整、
     * 不可安全重放）。
     *
     * <p>与 {@link ReplayDeterministicClock}（无上限，录制始终完整）对齐 cap 语义：使
     * “已达上限并丢弃”从静默行为变为<em>可检测</em>状态，供上层选择失败或标记为 incomplete。
     *
     * @return true 表示记录已被截断
     */
    public boolean isRecordLimitReached() {
        return recordLimitReached;
    }

    /**
     * 记录 UUID 时执行容量限制与截断告警。
     */
    private void appendRecordedUuid(UUID uuid) {
        if (recorded.size() >= MAX_RECORDS) {
            if (!recordLimitReached) {
                recordLimitReached = true;
                LOG.log(Level.WARNING,
                        String.format("UUID 记录数已达到上限 %d，后续记录将被丢弃", MAX_RECORDS));
            }
            return;
        }

        recorded.add(uuid);
    }

    /**
     * 重置记录状态，供进入重放模式前调用。
     */
    private void resetRecordingState() {
        this.recorded.clear();
        this.replayIndex = 0;
        this.recordLimitReached = false;
    }

    /**
     * 通过 ThreadLocalRandom 生成 version-4 UUID，保证性能与分布特性。
     */
    private UUID generateUuid() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long most = random.nextLong();
        long least = random.nextLong();
        most = (most & 0xffffffffffff0ffFL) | 0x0000000000004000L;
        least = (least & 0x3fffffffffffffffL) | 0x8000000000000000L;
        return new UUID(most, least);
    }
}
