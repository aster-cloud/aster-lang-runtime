package aster.runtime.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

/**
 * {@code shutdown()} 的契约（issue #42）。
 *
 * <p>★接口 javadoc 明写「此方法应<b>等待</b>所有进行中的 workflow 执行完成
 * <b>或超时</b>后再返回」，而实现直接 {@code cancel()} + {@code clear()}——
 * 既不等待也无超时。**注释声称 ≠ 实现**。
 *
 * <p>更糟的是被取消的 workflow <b>不写任何终态事件</b>：事件存储里这些 workflow
 * 永远停在非终态（只有 {@code WORKFLOW_STARTED}），事后回放/审计无法区分
 * 「还在跑」与「被 shutdown 掐断」。
 */
class ShutdownContractTest {

  /**
   * ★把 grace 调短到 300ms：本类要验证的是「超时后取消 + 补终态事件」这条路径，
   * 而不是「30 秒到底准不准」。生产默认仍是 30s（见 SHUTDOWN_GRACE）。
   */
  @BeforeAll
  static void shortenGrace() {
    System.setProperty("aster.workflow.shutdownGraceMillis", "300");
  }

  @AfterAll
  static void restoreGrace() {
    System.clearProperty("aster.workflow.shutdownGraceMillis");
  }

  @Test
  void shutdownWaitsForInFlightWorkflowToComplete() throws Exception {
    // ★契约的「等待」那半：已完成的 workflow 不该被改写成失败。
    var runtime = new InMemoryWorkflowRuntime();
    runtime.schedule("wf-done", null, new WorkflowMetadata());
    runtime.completeWorkflow("wf-done", "ok");

    runtime.shutdown();

    Optional<WorkflowState> st = runtime.getEventStore().getState("wf-done");
    assertTrue(st.isPresent(), "应有状态");
    assertEquals("ok", st.orElseThrow().getResult(),
        "shutdown 不得改写已完成 workflow 的终态");
  }

  @Test
  void cancelledWorkflowGetsTerminalEvent() {
    // ★核心回归：在途 workflow 被 shutdown 取消时必须留下终态事件。
    //   修复前只有 WORKFLOW_STARTED，事件存储永远停在非终态。
    var runtime = new InMemoryWorkflowRuntime();
    runtime.schedule("wf-hanging", null, new WorkflowMetadata());
    // 不 complete/fail —— 保持在途

    runtime.shutdown();

    Optional<WorkflowState> st = runtime.getEventStore().getState("wf-hanging");
    assertTrue(st.isPresent(), "应有状态");
    Object result = st.orElseThrow().getResult();
    assertNotNull(result, "被 shutdown 取消的 workflow 必须有终态事件，不得停在非终态");
    assertTrue(String.valueOf(result).contains("shutdown"),
        "终态原因须写明是 shutdown 取消，便于回放/审计区分「还在跑」与「被掐断」；实际：" + result);
  }

  @Test
  void shutdownReturnsPromptlyWhenNothingInFlight() {
    // ★反向护栏：没有在途执行时不得白等满 grace period。
    //   没有这条，把实现写成「无条件 sleep 30 秒」也能让上面两条变绿。
    var runtime = new InMemoryWorkflowRuntime();
    runtime.schedule("wf-a", null, new WorkflowMetadata());
    runtime.completeWorkflow("wf-a", "ok");

    long start = System.nanoTime();
    runtime.shutdown();
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertTrue(elapsedMs < 5_000,
        "无在途执行时应立即返回，实际耗时 " + elapsedMs + "ms（grace="
            + InMemoryWorkflowRuntime.SHUTDOWN_GRACE.toSeconds() + "s）");
  }

  @Test
  void shutdownIsIdempotent() {
    // 边界：重复 shutdown 不得抛异常。
    var runtime = new InMemoryWorkflowRuntime();
    runtime.schedule("wf-x", null, new WorkflowMetadata());
    runtime.shutdown();
    runtime.shutdown();
  }
}
