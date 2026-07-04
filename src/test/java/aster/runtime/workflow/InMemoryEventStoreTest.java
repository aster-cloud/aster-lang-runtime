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

  @Test
  void terminalStateIsALatchAtStoreLevel() {
    // 审计 #19 [MED] 防御层：store 直接被误用地追加第二个终态事件时，也必须拒绝
    // COMPLETED→FAILED，保持终态与序列一致。
    InMemoryEventStore store = new InMemoryEventStore();
    store.appendEvent("wf", WorkflowEvent.Type.WORKFLOW_STARTED, null);
    store.appendEvent("wf", WorkflowEvent.Type.WORKFLOW_COMPLETED, "done");
    long seqAfterComplete = store.getState("wf").orElseThrow().getLastEventSeq();

    // 第二次终态转移应被拒绝（no-op）。
    store.appendEvent("wf", WorkflowEvent.Type.WORKFLOW_FAILED, "should-be-rejected");

    WorkflowState state = store.getState("wf").orElseThrow();
    assertEquals(WorkflowState.Status.COMPLETED, state.getStatus(),
        "terminal state must not flip COMPLETED->FAILED");
    assertEquals(seqAfterComplete, state.getLastEventSeq(),
        "rejected terminal event must not advance the sequence");
    assertEquals("done", state.getResult());
  }

  @Test
  void throwablePayloadIsRedactedNotStoredRaw() {
    // 审计 #19 [LOW]：不得持久化原始 Throwable 链（栈/被包裹对象含 PII），
    // 仅保留 类名 + 单行 message。
    InMemoryEventStore store = new InMemoryEventStore();
    Throwable boom = new IllegalStateException("multi\nline\rsecret");
    store.appendEvent("wf", WorkflowEvent.Type.STEP_FAILED, boom);

    Object payload = store.getEvents("wf", 0).get(0).getPayload();
    assertFalse(payload instanceof Throwable, "raw Throwable must not be persisted");
    String s = payload.toString();
    assertTrue(s.contains("IllegalStateException"), "must retain the exception class");
    assertFalse(s.contains("\n") || s.contains("\r"), "message must be single-line/sanitized");
  }
}
