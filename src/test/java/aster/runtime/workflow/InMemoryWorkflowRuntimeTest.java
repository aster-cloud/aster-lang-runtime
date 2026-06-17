package aster.runtime.workflow;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

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
