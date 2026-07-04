package io.aster.workflow;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

  @Test
  void recordingPastCapIsDetectable() {
    // 审计 #19 [MED]：录制超过单 source 上限后不得静默丢弃——必须可检测。
    ReplayDeterministicRandom rnd = new ReplayDeterministicRandom();
    for (int i = 0; i < ReplayDeterministicRandom.MAX_RECORDS_PER_SOURCE; i++) {
      rnd.nextLong("s");
    }
    assertFalse(rnd.isRecordLimitReached(), "at exactly the cap the recording is still complete");
    assertTrue(rnd.getTruncatedSources().isEmpty());

    // 越过上限后：值仍返回给 workflow，但记录被丢弃 —— 现在这是可检测的。
    rnd.nextLong("s");
    assertTrue(rnd.isRecordLimitReached(), "recording past the cap must be detectable");
    assertTrue(rnd.getTruncatedSources().contains("s"));
    assertEquals(ReplayDeterministicRandom.MAX_RECORDS_PER_SOURCE,
        rnd.getRecordedRandoms().get("s").size(), "recorded values are capped");
  }

  @Test
  void enterReplayModeFlagsTruncationWhenInputExceedsCap() {
    // 从超长外部序列进入重放模式同样应被标记为截断。
    java.util.Map<String, List<Long>> oversized = new java.util.HashMap<>();
    List<Long> values = new ArrayList<>();
    for (int i = 0; i < ReplayDeterministicRandom.MAX_RECORDS_PER_SOURCE + 5; i++) {
      values.add((long) i);
    }
    oversized.put("s", values);

    ReplayDeterministicRandom rnd = new ReplayDeterministicRandom();
    rnd.enterReplayMode(oversized);
    assertTrue(rnd.isRecordLimitReached(), "truncated replay input must be detectable");
    assertTrue(rnd.getTruncatedSources().contains("s"));
  }
}
