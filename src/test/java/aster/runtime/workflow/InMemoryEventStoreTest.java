package aster.runtime.workflow;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 #5：getState/getLatestSnapshot 与写入走同一 per-workflow 锁，
 * 并发读写下读到的状态一致（lastEventSeq 单调，永不回滚）。
 */
class InMemoryEventStoreTest {

  @Test
  void getStateReflectsAppendedEvent() {
    InMemoryEventStore store = new InMemoryEventStore();
    store.appendEvent("wf", WorkflowEvent.Type.WORKFLOW_STARTED, null);
    Optional<WorkflowState> s = store.getState("wf");
    assertTrue(s.isPresent());
    assertEquals(WorkflowState.Status.READY, s.get().getStatus());
    assertEquals(1, s.get().getLastEventSeq());
  }

  @Test
  void getLatestSnapshotReturnsConsistentSnapshotAndSeq() {
    InMemoryEventStore store = new InMemoryEventStore();
    store.appendEvent("wf", WorkflowEvent.Type.WORKFLOW_STARTED, null);
    store.saveSnapshot("wf", 1L, "snap-data");

    Optional<WorkflowSnapshot> snap = store.getLatestSnapshot("wf");
    assertTrue(snap.isPresent());
    assertEquals("snap-data", snap.get().getState());
    assertEquals(1L, snap.get().getEventSeq());
  }

  @Test
  void concurrentReadsNeverSeeRegressingSequence() throws Exception {
    InMemoryEventStore store = new InMemoryEventStore();
    store.appendEvent("wf", WorkflowEvent.Type.WORKFLOW_STARTED, null);

    int writes = 2000;
    AtomicBoolean regressed = new AtomicBoolean(false);
    ExecutorService pool = Executors.newFixedThreadPool(4);
    try {
      Runnable writer = () -> {
        for (int i = 0; i < writes; i++) {
          store.appendEvent("wf", WorkflowEvent.Type.STEP_STARTED, null);
        }
      };
      Runnable reader = () -> {
        long last = 0;
        for (int i = 0; i < writes; i++) {
          long seq = store.getState("wf").map(WorkflowState::getLastEventSeq).orElse(0L);
          if (seq < last) {
            regressed.set(true);
          }
          last = seq;
        }
      };
      pool.submit(writer);
      pool.submit(reader);
      pool.submit(reader);
      pool.submit(writer);
    } finally {
      pool.shutdown();
      assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
    }
    assertFalse(regressed.get(), "lastEventSeq observed by a single reader must be monotonic");
  }
}
