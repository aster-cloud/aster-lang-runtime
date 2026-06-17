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
            synchronized (key.intern()) {
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
        // 同一 (key, entityId) 重复获取：返回既有句柄标识，确保重试不会重复启动
        // 一个新的 workflow（调用方据此复用已存在的 ExecutionHandle）。
        return Optional.of(current);
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
    }
}
