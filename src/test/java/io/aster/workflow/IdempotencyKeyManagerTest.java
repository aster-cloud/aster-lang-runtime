package io.aster.workflow;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 幂等键获取语义（issue #43 / #44）。
 *
 * <p>★两条路径必须**语义一致**。此前 fallback 路径对「占用者就是自己」返回
 * {@code Optional.of(current)}，而 CDI/cache 路径返回 {@code Optional.empty()}——
 * 同一 {@code (key, entityId)} 重复 tryAcquire 在两种部署形态下结论相反，
 * 而 javadoc 只描述一种语义。按 javadoc 使用的调用方必然在某一形态下拿到错误结果。
 *
 * <p>统一到 **empty = 我持有该键**（含「本就是我持有」）。这与唯一调用方
 * {@code InMemoryWorkflowRuntime.acquireOrDedupe} 的写法一致——它判的是
 * {@code existing.isEmpty() || workflowId.equals(existing.get())}，
 * 即两种返回都当作「我持有」，靠自行兜底抹平了这个分叉。
 *
 * <p>原测试注释担心的「重试重复启动 workflow」仍被防住：那由调用方的
 * {@code executions} 状态判定，不依赖本方法把「自己」谎报成「他人占用」。
 */
class IdempotencyKeyManagerTest {

  @Test
  void firstAcquireSucceedsAndReturnsEmpty() {
    IdempotencyKeyManager mgr = new IdempotencyKeyManager();
    Optional<String> r = mgr.tryAcquire("key-1", "wf-1", Duration.ofHours(1));
    assertTrue(r.isEmpty(), "first acquire should grant control (empty)");
  }

  @Test
  void reAcquireBySameEntityReturnsEmpty_matchingCachePath() {
    // ★与 CDI/cache 路径对齐（issue #44）：占用者就是自己 → empty（我持有）。
    //   原断言要求 fallback 返回 Optional.of，与 cache 路径相反，
    //   把「两条路径语义分叉」这个缺陷锁死了。
    IdempotencyKeyManager mgr = new IdempotencyKeyManager();
    assertTrue(mgr.tryAcquire("key-1", "wf-1", Duration.ofHours(1)).isEmpty());

    Optional<String> retry = mgr.tryAcquire("key-1", "wf-1", Duration.ofHours(1));
    assertTrue(retry.isEmpty(),
        "同一 (key, entityId) 重复获取应返回 empty（我持有），与 cache 路径一致");
  }

  @Test
  void twoManagerInstancesAreIndependent() {
    // ★issue #43：此前用 key.intern() 作监视器——interned 字符串是 JVM 全局共享对象，
    //   两个各有独立 cache 的 manager 实例会互相干扰，而它们本该互不相干。
    //   本条锁「实例隔离」这一可观测后果。
    IdempotencyKeyManager a = new IdempotencyKeyManager();
    IdempotencyKeyManager b = new IdempotencyKeyManager();

    assertTrue(a.tryAcquire("shared-key", "wf-a", Duration.ofHours(1)).isEmpty());
    assertTrue(b.tryAcquire("shared-key", "wf-b", Duration.ofHours(1)).isEmpty(),
        "另一个 manager 实例不得因同名 key 被占用而拿不到控制权");
  }

  @Test
  void lockStripesAreReclaimed() throws Exception {
    // ★换成 per-key 私有锁后必须显式回收锁条，否则把「全局锁串扰」换成了「内存泄漏」——
    //   而避免 side-map 无界增长正是原实现选择 intern() 的理由。
    //
    //   ★注意：**只有 CDI/cache 路径**会创建锁条（fallback 路径走 putIfAbsent，
    //   本身原子、不需要外层锁）。这是我写第一版测试时的错误假设——
    //   用默认构造器（fallback）跑，锁条恒为 0，前置条件直接失败。
    //   故这里直接往 keyLocks 里塞条目来模拟 cache 路径的产物，
    //   再验证 release/clear 确实回收。
    IdempotencyKeyManager mgr = new IdempotencyKeyManager();
    var stripes = lockStripes(mgr);
    for (int i = 0; i < 500; i++) {
      stripes.put("k-" + i, new Object());
    }
    assertEquals(500, lockStripeCount(mgr), "前置条件：应有 500 个锁条");

    for (int i = 0; i < 500; i++) {
      mgr.release("k-" + i);
    }
    assertEquals(0, lockStripeCount(mgr),
        "release 后锁条必须回收，否则 keyLocks 随 key 数量无界增长");

    // clear：cache 条目会**自行过期**而不经过 release，故需要显式清空口兜底。
    for (int i = 0; i < 50; i++) {
      stripes.put("expired-" + i, new Object());
    }
    assertTrue(lockStripeCount(mgr) > 0, "前置条件：应已产生锁条");
    mgr.clear();
    assertEquals(0, lockStripeCount(mgr), "clear 必须回收全部锁条");
  }

  @SuppressWarnings("unchecked")
  private java.util.Map<String, Object> lockStripes(IdempotencyKeyManager mgr) throws Exception {
    var f = IdempotencyKeyManager.class.getDeclaredField("keyLocks");
    f.setAccessible(true);
    return (java.util.Map<String, Object>) f.get(mgr);
  }

  @SuppressWarnings("unchecked")
  private int lockStripeCount(IdempotencyKeyManager mgr) throws Exception {
    var f = IdempotencyKeyManager.class.getDeclaredField("keyLocks");
    f.setAccessible(true);
    return ((java.util.Map<String, Object>) f.get(mgr)).size();
  }

  @Test
  void concurrentAcquireGrantsExactlyOneWinner() throws Exception {
    // ★锁的本职：并发抢同一个 key 时必须恰好一个赢家。
    //   换锁不能把互斥性弄丢——没有这条，把 synchronized 整个删掉也可能让其它测试全绿。
    IdempotencyKeyManager mgr = new IdempotencyKeyManager();
    int n = 32;
    var pool = java.util.concurrent.Executors.newFixedThreadPool(n);
    var start = new java.util.concurrent.CountDownLatch(1);
    var done = new java.util.concurrent.CountDownLatch(n);
    var winners = new java.util.concurrent.atomic.AtomicInteger();
    try {
      for (int i = 0; i < n; i++) {
        final int id = i;
        pool.submit(() -> {
          try {
            start.await();
            if (mgr.tryAcquire("hot", "wf-" + id, Duration.ofHours(1)).isEmpty()) {
              winners.incrementAndGet();
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            done.countDown();
          }
        });
      }
      start.countDown();
      assertTrue(done.await(30, java.util.concurrent.TimeUnit.SECONDS), "并发获取超时");
      assertEquals(1, winners.get(), "并发抢同一 key 必须恰好一个赢家");
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void differentEntityIsRejectedWithExistingId() {
    IdempotencyKeyManager mgr = new IdempotencyKeyManager();
    assertTrue(mgr.tryAcquire("key-1", "wf-1", Duration.ofHours(1)).isEmpty());

    Optional<String> other = mgr.tryAcquire("key-1", "wf-2", Duration.ofHours(1));
    assertTrue(other.isPresent());
    assertEquals("wf-1", other.get(), "should report the original owner");
  }

  @Test
  void releaseAllowsReacquire() {
    IdempotencyKeyManager mgr = new IdempotencyKeyManager();
    assertTrue(mgr.tryAcquire("key-1", "wf-1", Duration.ofHours(1)).isEmpty());
    mgr.release("key-1");
    assertTrue(mgr.tryAcquire("key-1", "wf-2", Duration.ofHours(1)).isEmpty(),
        "after release a new owner can acquire freshly");
  }

  @Test
  void clearInvalidatesQuarkusCacheInCdiMode() {
    // 审计 #56：CDI 构造时 fallbackCache 为 null，此前 clear() 只清锁条、cache 中的键原样保留，
    // 与 javadoc「清空全部幂等键」不符——重置后旧键仍占用，新 owner 被判「已被占用」。
    MapBackedCache cache = new MapBackedCache();
    IdempotencyKeyManager mgr = new IdempotencyKeyManager(cache);
    assertTrue(mgr.tryAcquire("key-1", "wf-1", Duration.ofHours(1)).isEmpty());
    assertTrue(mgr.tryAcquire("key-2", "wf-2", Duration.ofHours(1)).isEmpty());
    assertEquals(2, cache.entries.size(), "前置条件：两个键均已写入 cache");

    mgr.clear();

    assertTrue(cache.entries.isEmpty(), "clear 必须清空 Quarkus cache 中的全部键");
    assertTrue(mgr.tryAcquire("key-1", "wf-9", Duration.ofHours(1)).isEmpty(),
        "clear 后新 owner 必须能重新获得键");
  }

  /** 以并发 Map 模拟 Quarkus Cache：只实现 tryAcquire/release/clear 用到的原子语义。 */
  private static final class MapBackedCache implements io.quarkus.cache.Cache {
    final java.util.concurrent.ConcurrentHashMap<Object, Object> entries =
        new java.util.concurrent.ConcurrentHashMap<>();

    @Override public String getName() { return "idempotency-keys"; }
    @Override public Object getDefaultKey() { return null; }

    @Override @SuppressWarnings("unchecked")
    public <K, V> io.smallrye.mutiny.Uni<V> get(K key, java.util.function.Function<K, V> loader) {
      return io.smallrye.mutiny.Uni.createFrom().item(
          (V) entries.computeIfAbsent(key, k -> loader.apply((K) k)));
    }

    @Override
    public <K, V> io.smallrye.mutiny.Uni<V> getAsync(K key,
        java.util.function.Function<K, io.smallrye.mutiny.Uni<V>> loader) {
      return get(key, k -> loader.apply(k).await().indefinitely());
    }

    @Override public io.smallrye.mutiny.Uni<Void> invalidate(Object key) {
      entries.remove(key);
      return io.smallrye.mutiny.Uni.createFrom().voidItem();
    }

    @Override public io.smallrye.mutiny.Uni<Void> invalidateAll() {
      entries.clear();
      return io.smallrye.mutiny.Uni.createFrom().voidItem();
    }

    @Override public io.smallrye.mutiny.Uni<Void> invalidateIf(java.util.function.Predicate<Object> predicate) {
      entries.keySet().removeIf(predicate);
      return io.smallrye.mutiny.Uni.createFrom().voidItem();
    }

    @Override public <T extends io.quarkus.cache.Cache> T as(Class<T> type) { return type.cast(this); }
  }
}
