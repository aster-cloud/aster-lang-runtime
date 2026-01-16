package aster.runtime;

import java.util.concurrent.*;

public final class Async {
  private Async() {}
  public static final Executor DEFAULT = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
  @FunctionalInterface public interface SupplierWithEx<T>{ T get() throws Exception; }
  public static <T> CompletableFuture<T> async(SupplierWithEx<T> s) {
    return CompletableFuture.supplyAsync(() -> {
      try { return s.get(); }
      catch (RuntimeException re) { throw re; }
      catch (Exception e) { throw new CompletionException(e); }
    }, DEFAULT);
  }
}

