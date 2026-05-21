package aster.runtime;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Aster 运行时异步执行入口。
 *
 * <p>关键设计：
 * <ul>
 *   <li><b>守护线程</b>：执行池由 {@link ThreadFactory} 创建守护线程，
 *       避免 native-image 或 JVM 嵌入场景下被遗忘的工作线程阻止进程退出。</li>
 *   <li><b>JVM shutdown 钩子</b>：注册 shutdown 钩子在 JVM 退出时优雅关闭
 *       executor，避免线程上下文泄漏；shutdown 超时后强制中断。</li>
 *   <li><b>线程命名</b>：固定前缀 {@code aster-async-} 方便 thread dump
 *       排障定位。</li>
 *   <li><b>大小可配</b>：通过系统属性 {@code aster.runtime.async.threads}
 *       覆盖默认池大小（默认 {@code availableProcessors()}）；非法值回退默认。</li>
 * </ul>
 *
 * <p>原实现使用静态 {@code Executors.newFixedThreadPool}，存在以下问题：
 * 非守护线程导致 JVM 无法退出、无 shutdown 钩子导致优雅停止失败、
 * 无大小配置入口导致 GraalVM native-image 测试场景需要硬编码改源码。
 * 本次修复全部解决。
 */
public final class Async {
  private Async() {}

  /** 系统属性键：覆盖默认线程池大小。 */
  public static final String THREADS_PROPERTY = "aster.runtime.async.threads";

  /** 内部 ExecutorService 句柄（用于 shutdown 钩子与测试可见性）。 */
  private static final ExecutorService POOL = createDefaultPool();

  /** 对外暴露的 Executor 接口；保持二进制兼容（原签名为 Executor）。 */
  public static final java.util.concurrent.Executor DEFAULT = POOL;

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(Async::shutdown, "aster-async-shutdown"));
  }

  private static ExecutorService createDefaultPool() {
    int size = resolvePoolSize();
    ThreadFactory factory = new ThreadFactory() {
      private final AtomicInteger counter = new AtomicInteger(1);
      @Override
      public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "aster-async-" + counter.getAndIncrement());
        t.setDaemon(true);
        return t;
      }
    };
    return Executors.newFixedThreadPool(size, factory);
  }

  private static int resolvePoolSize() {
    String prop = System.getProperty(THREADS_PROPERTY);
    if (prop != null && !prop.isEmpty()) {
      try {
        int parsed = Integer.parseInt(prop.trim());
        if (parsed > 0) {
          return parsed;
        }
      } catch (NumberFormatException ignored) {
        // 非法值回退默认
      }
    }
    return Math.max(1, Runtime.getRuntime().availableProcessors());
  }

  /**
   * 优雅关闭默认 executor。
   *
   * <p>正常 JVM 退出会通过 shutdown 钩子自动调用本方法。手动嵌入场景
   * （例如 Quarkus 应用收到 SIGTERM 后想立即回收 Aster 线程）可显式调用。
   * 等待 2 秒后任务未完成则强制中断。
   */
  public static void shutdown() {
    POOL.shutdown();
    try {
      if (!POOL.awaitTermination(2, TimeUnit.SECONDS)) {
        POOL.shutdownNow();
      }
    } catch (InterruptedException e) {
      POOL.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  @FunctionalInterface public interface SupplierWithEx<T>{ T get() throws Exception; }

  public static <T> CompletableFuture<T> async(SupplierWithEx<T> s) {
    return CompletableFuture.supplyAsync(() -> {
      try { return s.get(); }
      catch (RuntimeException re) { throw re; }
      catch (Exception e) { throw new CompletionException(e); }
    }, DEFAULT);
  }
}
