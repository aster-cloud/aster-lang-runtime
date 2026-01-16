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
        return THREAD_LOCAL.get();
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
