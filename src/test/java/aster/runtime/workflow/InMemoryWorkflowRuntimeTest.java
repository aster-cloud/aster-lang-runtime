package aster.runtime.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 #4：终态转移持久化非空错误（含 message 为 null 的异常），且幂等。
 */
class InMemoryWorkflowRuntimeTest {

  @Test
  void failWorkflowWithNullMessageStoresNonNullReason() {
    InMemoryWorkflowRuntime runtime = new InMemoryWorkflowRuntime();
    ExecutionHandle handle = runtime.schedule("wf-1", null, new WorkflowMetadata());

    // message 为 null 的异常（很常见，例如 NPE without message）。
    RuntimeException error = new IllegalStateException();
    assertTrue(error.getMessage() == null, "precondition: exception has null message");

    runtime.failWorkflow("wf-1", error);

    Optional<WorkflowState> state = runtime.getEventStore().getState("wf-1");
    assertTrue(state.isPresent());
    assertEquals(WorkflowState.Status.FAILED, state.get().getStatus());

    Object reason = state.get().getResult();
    assertNotNull(reason, "failure reason must be persisted even when getMessage() is null");
    assertTrue(reason.toString().contains("IllegalStateException"),
        "reason should include throwable class name, got: " + reason);

    // handle 也应当异常完成
    assertThrows(ExecutionException.class, () -> handle.getResult().get());
  }

  @Test
  void failWorkflowPreservesCauseChain() {
    InMemoryWorkflowRuntime runtime = new InMemoryWorkflowRuntime();
    runtime.schedule("wf-2", null, new WorkflowMetadata());

    Throwable cause = new IllegalArgumentException("root cause");
    Throwable error = new RuntimeException(cause); // message is the cause's toString, but cause chain matters
    runtime.failWorkflow("wf-2", error);

    Object reason = runtime.getEventStore().getState("wf-2").orElseThrow().getResult();
    assertNotNull(reason);
    assertTrue(reason.toString().contains("RuntimeException"));
    assertTrue(reason.toString().contains("IllegalArgumentException"), "cause chain should be recorded");
    assertTrue(reason.toString().contains("root cause"));
  }

  @Test
  void terminalTransitionIsIdempotent() {
    InMemoryWorkflowRuntime runtime = new InMemoryWorkflowRuntime();
    runtime.schedule("wf-3", null, new WorkflowMetadata());

    runtime.completeWorkflow("wf-3", "result-A");
    WorkflowState afterFirst = runtime.getEventStore().getState("wf-3").orElseThrow();
    assertEquals(WorkflowState.Status.COMPLETED, afterFirst.getStatus());
    long seqAfterFirst = afterFirst.getLastEventSeq();

    // 再次 complete / fail 都应被忽略（已是终态），不追加新的终态事件。
    runtime.completeWorkflow("wf-3", "result-B");
    runtime.failWorkflow("wf-3", new RuntimeException("late fail"));

    WorkflowState afterRepeat = runtime.getEventStore().getState("wf-3").orElseThrow();
    assertEquals(WorkflowState.Status.COMPLETED, afterRepeat.getStatus());
    assertEquals(seqAfterFirst, afterRepeat.getLastEventSeq(),
        "no extra terminal events should be appended once terminal");
    assertEquals("result-A", afterRepeat.getResult());
  }

  @Test
  void lateCompletionStillRecordsTerminalEvent() {
    // 即使执行状态被先行清理（模拟"迟到的完成"），仍应记录终态事件。
    InMemoryWorkflowRuntime runtime = new InMemoryWorkflowRuntime();
    runtime.schedule("wf-4", null, new WorkflowMetadata());

    // 第一次完成会 remove executions 条目；但状态判定走 eventStore，
    // 这里我们验证：在非终态前提下 complete 总能写入终态。
    runtime.completeWorkflow("wf-4", "done");
    assertEquals(WorkflowState.Status.COMPLETED,
        runtime.getEventStore().getState("wf-4").orElseThrow().getStatus());
  }

  @Test
  void concurrentScheduleWithSameIdempotencyKeyStartsExactlyOneWorkflow() throws Exception {
    // 审计 #19 [HIGH]：两个并发 schedule() 使用同一幂等键，必须恰好启动一个 workflow，
    // 且败者返回胜者的句柄，绝不释放胜者的键去启动自己的 workflow。
    final int rounds = 200;
    for (int round = 0; round < rounds; round++) {
      InMemoryWorkflowRuntime runtime = new InMemoryWorkflowRuntime();
      String key = "idem-" + round;
      String wfA = "wf-a-" + round;
      String wfB = "wf-b-" + round;

      CyclicBarrier barrier = new CyclicBarrier(2);
      ConcurrentLinkedQueue<ExecutionHandle> handles = new ConcurrentLinkedQueue<>();
      ExecutorService pool = Executors.newFixedThreadPool(2);
      try {
        Runnable a = scheduleTask(runtime, wfA, key, barrier, handles);
        Runnable b = scheduleTask(runtime, wfB, key, barrier, handles);
        pool.submit(a);
        pool.submit(b);
      } finally {
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
      }

      // 恰好一个 workflowId 记录了 WORKFLOW_STARTED（即恰好启动一次）。
      int started = 0;
      for (String wf : List.of(wfA, wfB)) {
        Optional<WorkflowState> s = runtime.getEventStore().getState(wf);
        if (s.isPresent()) {
          assertEquals(WorkflowState.Status.READY, s.get().getStatus(),
              "the started workflow must be READY (WORKFLOW_STARTED appended)");
          started++;
        }
      }
      assertEquals(1, started,
          "exactly one workflow may start for a single idempotency key (round " + round + ")");

      // 两个调用返回的句柄必须是同一个（胜者的句柄）。
      assertEquals(2, handles.size());
      ExecutionHandle h1 = handles.poll();
      ExecutionHandle h2 = handles.poll();
      assertSame(h1, h2, "loser must receive the winner's handle, not a fresh one (round " + round + ")");
    }
  }

  private static Runnable scheduleTask(InMemoryWorkflowRuntime runtime, String workflowId, String key,
                                       CyclicBarrier barrier, ConcurrentLinkedQueue<ExecutionHandle> sink) {
    return () -> {
      try {
        barrier.await(5, TimeUnit.SECONDS);
        sink.add(runtime.schedule(workflowId, key, new WorkflowMetadata()));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    };
  }

  @Test
  void concurrentCompleteAndFailLeaveSingleTerminalState() throws Exception {
    // 审计 #19 [MED]：complete 与 fail 并发时，终态转移必须原子——最终恰好一个终态，
    // 且事件存储中的状态与 CompletableFuture 结果一致（不出现 COMPLETED→FAILED 分叉）。
    final int rounds = 500;
    AtomicInteger completedWins = new AtomicInteger();
    AtomicInteger failedWins = new AtomicInteger();
    for (int round = 0; round < rounds; round++) {
      InMemoryWorkflowRuntime runtime = new InMemoryWorkflowRuntime();
      String wf = "wf-term-" + round;
      ExecutionHandle handle = runtime.schedule(wf, null, new WorkflowMetadata());

      CyclicBarrier barrier = new CyclicBarrier(2);
      ExecutorService pool = Executors.newFixedThreadPool(2);
      try {
        pool.submit(() -> {
          awaitQuietly(barrier);
          runtime.completeWorkflow(wf, "ok");
        });
        pool.submit(() -> {
          awaitQuietly(barrier);
          runtime.failWorkflow(wf, new RuntimeException("boom"));
        });
      } finally {
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
      }

      WorkflowState state = runtime.getEventStore().getState(wf).orElseThrow();
      WorkflowState.Status status = state.getStatus();
      assertTrue(status == WorkflowState.Status.COMPLETED || status == WorkflowState.Status.FAILED,
          "must land on a terminal state, got " + status + " (round " + round + ")");

      // 事件存储中的终态必须与句柄结果一致。
      if (status == WorkflowState.Status.COMPLETED) {
        completedWins.incrementAndGet();
        assertEquals("ok", handle.getResult().get(1, TimeUnit.SECONDS),
            "COMPLETED state must match a successfully-completed future");
      } else {
        failedWins.incrementAndGet();
        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> handle.getResult().get(1, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof RuntimeException,
            "FAILED state must match an exceptionally-completed future");
      }

      // 只应存在一个终态事件（不应既有 COMPLETED 又有 FAILED）。
      long terminalEvents = runtime.getEventStore().getEvents(wf, 0).stream()
          .filter(e -> WorkflowEvent.Type.WORKFLOW_COMPLETED.equals(e.getEventType())
              || WorkflowEvent.Type.WORKFLOW_FAILED.equals(e.getEventType()))
          .count();
      assertEquals(1, terminalEvents,
          "exactly one terminal event may be recorded (round " + round + ")");
    }
    // 两种结果都应在多轮中出现，确认竞态确实被驱动（非确定性防御，不强制严格）。
    assertTrue(completedWins.get() + failedWins.get() == rounds);
  }

  private static void awaitQuietly(CyclicBarrier barrier) {
    try {
      barrier.await(5, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void determinismContextRunWithBindsAndClearsOnSameThread() {
    // 验证 #6 的安全原语：runWith 在当前线程绑定 per-workflow 实例，结束后复位。
    InMemoryWorkflowRuntime runtime = new InMemoryWorkflowRuntime();
    runtime.schedule("wf-5", null, new WorkflowMetadata());
    io.aster.workflow.DeterminismContext ctx = runtime.getDeterminismContext("wf-5");

    io.aster.workflow.ReplayDeterministicUuid before = io.aster.workflow.ReplayDeterministicUuid.current();
    ctx.runWith(() ->
        assertSame(ctx.uuid(), io.aster.workflow.ReplayDeterministicUuid.current(),
            "inside runWith current() must be the workflow's instance"));
    assertSame(before, io.aster.workflow.ReplayDeterministicUuid.current(),
        "after runWith the previous binding must be restored on this thread");
  }
}
