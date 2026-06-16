package io.aster.workflow;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 #3：非 CDI 回退路径下，重复获取同一 (key, entityId) 返回既有句柄
 * （Optional.of），而不是"刚获取"（Optional.empty），从而避免重试重复启动。
 */
class IdempotencyKeyManagerTest {

  @Test
  void firstAcquireSucceedsAndReturnsEmpty() {
    IdempotencyKeyManager mgr = new IdempotencyKeyManager();
    Optional<String> r = mgr.tryAcquire("key-1", "wf-1", Duration.ofHours(1));
    assertTrue(r.isEmpty(), "first acquire should grant control (empty)");
  }

  @Test
  void reAcquireSameKeyReturnsExistingHandle() {
    IdempotencyKeyManager mgr = new IdempotencyKeyManager();
    assertTrue(mgr.tryAcquire("key-1", "wf-1", Duration.ofHours(1)).isEmpty());

    // 同一 key + 同一 entityId 重试：必须返回既有 entityId，而不是 empty。
    Optional<String> retry = mgr.tryAcquire("key-1", "wf-1", Duration.ofHours(1));
    assertTrue(retry.isPresent(), "re-acquire must report the existing handle, not 'newly acquired'");
    assertEquals("wf-1", retry.get());
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
}
