package io.aster.workflow;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 #2：nextDouble 产生 [0,1) 区间内有限的均匀 double，并且重放确定。
 */
class ReplayDeterministicRandomTest {

  @Test
  void nextDoubleIsFiniteAndInUnitInterval() {
    ReplayDeterministicRandom rnd = new ReplayDeterministicRandom();
    for (int i = 0; i < 10_000; i++) {
      double d = rnd.nextDouble("source");
      assertTrue(Double.isFinite(d), "draw must be finite, got " + d);
      assertTrue(d >= 0.0 && d < 1.0, "draw must be in [0,1), got " + d);
    }
  }

  @Test
  void replayReproducesSameSequence() {
    // 录制一段 double 序列对应的底层 long 记录。
    ReplayDeterministicRandom recorder = new ReplayDeterministicRandom();
    List<Double> first = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      first.add(recorder.nextDouble("s"));
    }

    // 用录制的 long 序列进入重放模式，应得到完全相同的 double 序列。
    ReplayDeterministicRandom replayer = new ReplayDeterministicRandom();
    replayer.enterReplayMode(recorder.getRecordedRandoms());
    List<Double> second = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      second.add(replayer.nextDouble("s"));
    }

    assertEquals(first, second, "replay must reproduce the recorded sequence deterministically");
  }
}
