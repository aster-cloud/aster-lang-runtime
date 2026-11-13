package io.aster.workflow;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 三个重放门面（时钟 / UUID / 随机数）在多线程并发记录时不丢记录、不抛异常。
 * 起因：truffle AsyncTaskRegistry.scheduleRetry 从多个 worker 并发调 now()，
 * 无锁的 ArrayList 追加在 CI 上抛 ArrayIndexOutOfBoundsException。
 */
class ReplayFacadeConcurrencyTest {

  private static final int THREADS = 8;

  @Test
  void clockRecordsEveryConcurrentCall() throws Exception {
    ReplayDeterministicClock clock = new ReplayDeterministicClock();
    int perThread = 5_000;
    runConcurrently(t -> {
      for (int i = 0; i < perThread; i++) {
        clock.now();
      }
    });
    assertEquals(THREADS * perThread, clock.getRecordedTimes().size());
  }

  @Test
  void uuidRecordsEveryConcurrentCallWithinCap() throws Exception {
    ReplayDeterministicUuid uuid = new ReplayDeterministicUuid();
    int perThread = ReplayDeterministicUuid.MAX_RECORDS / THREADS;
    runConcurrently(t -> {
      for (int i = 0; i < perThread; i++) {
        uuid.randomUUID();
      }
    });
    assertEquals(THREADS * perThread, uuid.getRecordedUuids().size());
  }

  @Test
  void randomRecordsEveryConcurrentCallPerSource() throws Exception {
    ReplayDeterministicRandom random = new ReplayDeterministicRandom();
    int perThread = 400;
    runConcurrently(t -> {
      for (int i = 0; i < perThread; i++) {
        random.nextLong("shared");
        random.nextLong("source-" + t);
      }
    });
    var recorded = random.getRecordedRandoms();
    // shared 被所有线程写，总数超过每 source 上限，按上限截断；各线程独占的 source 不截断。
    assertEquals(ReplayDeterministicRandom.MAX_RECORDS_PER_SOURCE, recorded.get("shared").size());
    for (int t = 0; t < THREADS; t++) {
      assertEquals(perThread, recorded.get("source-" + t).size());
    }
  }

  private interface Worker {
    void run(int threadIndex) throws Exception;
  }

  /** 所有线程同时起跑；逐个 get() 让 worker 内的异常原样抛出。 */
  private static void runConcurrently(Worker worker) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();
    try {
      for (int t = 0; t < THREADS; t++) {
        final int threadIndex = t;
        futures.add(executor.submit(() -> {
          start.await();
          worker.run(threadIndex);
          return null;
        }));
      }
      start.countDown();
      for (Future<?> f : futures) {
        f.get(30, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }
  }
}
