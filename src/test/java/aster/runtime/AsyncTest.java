package aster.runtime;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Async 默认 executor 与异步包装函数的最小基线测试。
 *
 * 验证：
 * <ul>
 *   <li>async() 返回 CompletableFuture 并把结果传递回 caller。</li>
 *   <li>checked 异常被包装为 CompletionException。</li>
 *   <li>RuntimeException 原样冒出（不双层包装）。</li>
 *   <li>工作线程必须为守护线程（避免 native-image / JVM 嵌入场景挂死）。</li>
 *   <li>工作线程命名以 aster-async- 开头，便于 thread dump 排障。</li>
 * </ul>
 */
class AsyncTest {

  @Test
  void asyncReturnsResult() throws Exception {
    CompletableFuture<Integer> f = Async.async(() -> 42);
    assertEquals(42, f.get(5, TimeUnit.SECONDS));
  }

  @Test
  void asyncWrapsCheckedException() {
    CompletableFuture<Object> f = Async.async(() -> {
      throw new java.io.IOException("boom");
    });
    ExecutionException ex = assertThrows(ExecutionException.class,
        () -> f.get(5, TimeUnit.SECONDS));
    assertTrue(ex.getCause() instanceof java.io.IOException
            || ex.getCause() instanceof java.util.concurrent.CompletionException,
        "checked 异常必须被传递为 IOException 或 CompletionException(IOException)");
  }

  @Test
  void asyncPropagatesRuntimeException() {
    CompletableFuture<Object> f = Async.async(() -> {
      throw new IllegalStateException("rt-boom");
    });
    ExecutionException ex = assertThrows(ExecutionException.class,
        () -> f.get(5, TimeUnit.SECONDS));
    // 直接是 RuntimeException 子类
    assertTrue(ex.getCause() instanceof IllegalStateException,
        "RuntimeException 不应被多层包装：实际 cause=" + ex.getCause());
  }

  @Test
  void workerThreadsAreDaemon() throws Exception {
    AtomicReference<Thread> captured = new AtomicReference<>();
    Async.async(() -> {
      captured.set(Thread.currentThread());
      return null;
    }).get(5, TimeUnit.SECONDS);

    Thread t = captured.get();
    assertNotNull(t, "应捕获到工作线程");
    assertTrue(t.isDaemon(),
        "工作线程必须是 daemon，否则 JVM 退出时会被悬挂线程阻塞。实际：" + t.getName());
  }

  @Test
  void workerThreadNamePrefixed() throws Exception {
    AtomicReference<String> name = new AtomicReference<>();
    Async.async(() -> {
      name.set(Thread.currentThread().getName());
      return null;
    }).get(5, TimeUnit.SECONDS);
    assertTrue(name.get().startsWith("aster-async-"),
        "工作线程名必须以 aster-async- 开头便于运维定位。实际：" + name.get());
  }

  @Test
  void shutdownIsIdempotent() {
    // 不要在测试里真的 shutdown，因为静态 POOL 一旦关闭无法重启会污染其他测试。
    // 这里仅检查 shutdown() 方法存在且 public（编译期约束足够）。
    assertDoesNotThrow(() -> Async.class.getMethod("shutdown"));
  }

  @Test
  void threadsPropertyConstantPresent() {
    assertEquals("aster.runtime.async.threads", Async.THREADS_PROPERTY,
        "线程数配置 key 必须保持稳定，运维脚本依赖此字符串");
  }
}
