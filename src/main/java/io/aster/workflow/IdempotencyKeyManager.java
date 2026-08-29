package io.aster.workflow;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 幂等性键管理器，负责在 Quarkus Caffeine 缓存中原子地获取和释放键。
 *
 * 利用 Cache.get() 的原子性，确保高并发场景下同一键只会被单个实体占用。
 * 在非 CDI 场景下（例如单元测试）会自动退化为基于 ConcurrentHashMap 的本地实现，保证线程安全。
 */
@ApplicationScoped
public class IdempotencyKeyManager {

    private final Cache cache;
    private final Map<String, String> fallbackCache;

    /**
     * per-key 私有锁条（striped lock）—— **实例私有**，不与 JVM 任何其它代码共享（issue #43）。
     *
     * <p>★此前用 {@code key.intern()} 作监视器。interned 字符串是 **JVM 全局共享对象**：
     * 任何其它代码对相同字面量字符串加锁都会与此处竞争同一把锁（潜在死锁/串扰），
     * 而且两个 manager 实例（各有独立 cache）也会互相干扰——它们本该互不相干。
     * 幂等键来自调用方的任意字符串，撞上某个常用字面量并非小概率事件。
     *
     * <p>原注释只论证了「避免 side-map 无界增长」，那个顾虑是真的，但代价选错了。
     * 现用 {@code ConcurrentHashMap} + {@code computeIfAbsent} 拿 per-key 私有锁对象，
     * 并在 {@link #release(String)} / {@link #clear()} 里同步移除锁条——
     * 增长问题由**显式清理**解决，而不是靠借用全局对象绕开。
     */
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    /**
     * 默认构造函数：提供非 CDI 场景的本地并发 Map 实现。
     */
    public IdempotencyKeyManager() {
        this.cache = null;
        this.fallbackCache = new ConcurrentHashMap<>();
    }

    /**
     * CDI 构造函数：注入 Quarkus Caffeine 缓存实例。
     */
    @Inject
    public IdempotencyKeyManager(@CacheName("idempotency-keys") Cache cache) {
        this.cache = Objects.requireNonNull(cache, "缓存实例不能为空");
        this.fallbackCache = null;
    }

    /**
     * 尝试原子获取幂等性键。
     *
     * @param key 幂等性键
     * @param entityId 对应实体 ID（workflowId）
     * @param ttl 客户端期望的 TTL（实际过期由缓存配置控制）
     * @return Optional.empty 表示成功获得控制权，Optional.of(existingId) 表示已被 existingId 占用
     */
    public Optional<String> tryAcquire(String key, String entityId, Duration ttl) {
        Objects.requireNonNull(key, "幂等性键不能为空");
        Objects.requireNonNull(entityId, "实体 ID 不能为空");
        if (ttl != null && ttl.isNegative()) {
            throw new IllegalArgumentException("TTL 不能为负数");
        }

        if (cache != null) {
            // localLocks 仅用于把"读取-或-写入"序列化到同一 key 上。为避免该
            // side-map 随 key 数量无界增长（缓存条目会过期，但 localLocks 不会），
            // 采用 key 的 intern 字符串本身作为监视器，从而不需要单独维护一张表。
            // 锁对象来自实例私有的 keyLocks，而非 key.intern()（issue #43）。
            synchronized (keyLocks.computeIfAbsent(key, k -> new Object())) {
                String existing = cache.get(key, k -> entityId).await().indefinitely();
                // 首次写入返回我们自己的 entityId（empty=获得控制权）；
                // 否则返回既有占用者。
                return entityId.equals(existing) ? Optional.empty() : Optional.of(existing);
            }
        }

        String current = fallbackCache.putIfAbsent(key, entityId);
        if (current == null) {
            // 首次获取，成功获得控制权。
            return Optional.empty();
        }
        // ★与 cache 路径**语义一致**（issue #44）：占用者就是自己时同样返回 empty。
        //   此前这里不比较 entityId，凡 current != null 一律 Optional.of(current)——
        //   于是同一 (key, entityId) 重复 tryAcquire 在两种部署形态下结论相反：
        //   CDI/cache 路径说「你获得了控制权」，fallback 路径说「已被占用」。
        //   javadoc 只描述一种语义，按它使用的调用方必然在某一形态下拿到错误结果。
        return entityId.equals(current) ? Optional.empty() : Optional.of(current);
    }

    /**
     * 手动释放幂等性键，便于在 Workflow 完成或失败后提前清理。
     *
     * @param key 幂等性键
     */
    public void release(String key) {
        Objects.requireNonNull(key, "幂等性键不能为空");
        if (cache != null) {
            cache.invalidate(key).await().indefinitely();
        } else {
            fallbackCache.remove(key);
        }
        // ★同步移除锁条，否则 keyLocks 会随 key 数量无界增长——
        //   那正是原实现用 key.intern() 想避开的问题（issue #43）。
        //   用显式清理解决，而不是借用 JVM 全局对象。
        keyLocks.remove(key);
    }

    /**
     * 清空全部幂等键与锁条（测试/重置用）。
     *
     * <p>★存在的理由：keyLocks 只在 {@link #release(String)} 里回收，而 cache 条目
     * 会**自行过期**——过期路径不经过 release，故长生命周期进程里仍可能残留锁条。
     * 提供显式清空口，让调用方（如测试、租户重置）能一次性回收。
     */
    public void clear() {
        if (fallbackCache != null) {
            fallbackCache.clear();
        }
        keyLocks.clear();
    }
}
