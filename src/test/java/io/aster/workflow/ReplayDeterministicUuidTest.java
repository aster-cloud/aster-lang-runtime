package io.aster.workflow;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ReplayDeterministicUuid 直测（审计 #19 指出此前零直接测试）。
 * 覆盖：record→replay 确定性、耗尽异常、以及记录上限的可检测性 [MED]。
 */
class ReplayDeterministicUuidTest {

  @Test
  void replayReproducesRecordedSequence() {
    ReplayDeterministicUuid recorder = new ReplayDeterministicUuid();
    List<UUID> first = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      first.add(recorder.randomUUID());
    }

    ReplayDeterministicUuid replayer = new ReplayDeterministicUuid();
    replayer.enterReplayMode(recorder.getRecordedUuids());
    List<UUID> second = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      second.add(replayer.randomUUID());
    }
    assertEquals(first, second, "replay must reproduce the recorded UUID sequence");
  }

  @Test
  void replayExhaustionThrows() {
    ReplayDeterministicUuid replayer = new ReplayDeterministicUuid();
    replayer.enterReplayMode(List.of(UUID.randomUUID()));
    replayer.randomUUID(); // consume the only recorded value
    assertThrows(IllegalStateException.class, replayer::randomUUID,
        "replay must throw when the recorded sequence is exhausted");
  }

  @Test
  void generatesVersion4Uuids() {
    ReplayDeterministicUuid gen = new ReplayDeterministicUuid();
    for (int i = 0; i < 100; i++) {
      UUID u = gen.randomUUID();
      assertEquals(4, u.version(), "must be a version-4 UUID");
      assertEquals(2, u.variant(), "must be the IETF variant");
    }
  }

  @Test
  void recordingPastCapIsDetectable() {
    // 审计 #19 [MED]：超过上限后记录被丢弃，但必须可检测，而非静默。
    ReplayDeterministicUuid gen = new ReplayDeterministicUuid();
    for (int i = 0; i < ReplayDeterministicUuid.MAX_RECORDS; i++) {
      gen.randomUUID();
    }
    assertFalse(gen.isRecordLimitReached(), "at exactly the cap the recording is still complete");

    gen.randomUUID(); // one past the cap
    assertTrue(gen.isRecordLimitReached(), "recording past the cap must be detectable");
    assertEquals(ReplayDeterministicUuid.MAX_RECORDS, gen.getRecordedUuids().size(),
        "recorded UUIDs are capped");
  }
}
