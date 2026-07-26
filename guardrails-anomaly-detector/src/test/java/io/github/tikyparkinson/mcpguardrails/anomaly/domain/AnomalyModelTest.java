/*
 * Copyright 2026 TikyParkinson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.tikyparkinson.mcpguardrails.anomaly.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AnomalyModelTest {

  private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");
  private static final ArgumentsFingerprint FINGERPRINT = ArgumentsFingerprint.of(Map.of("q", "x"));

  @Test
  void shouldRejectAnInvocationWithABlankAgent() {
    // given a record with no agent
    // when constructed
    // then it fails: history is grouped by agent and a blank one would merge unrelated agents
    assertThrows(
        IllegalArgumentException.class,
        () -> new InvocationRecord(" ", "search", FINGERPRINT, NOW));
  }

  @Test
  void shouldRejectAnInvocationWithABlankTool() {
    // given a record with no tool name
    // when constructed
    // then it fails
    assertThrows(
        IllegalArgumentException.class, () -> new InvocationRecord("agent", "", FINGERPRINT, NOW));
  }

  @Test
  void shouldRejectAnInvocationWithoutAnInstant() {
    // given a record with no instant
    // when constructed
    // then it fails: without it the record belongs to no window
    assertThrows(
        NullPointerException.class,
        () -> new InvocationRecord("agent", "search", FINGERPRINT, null));
  }

  @Test
  void shouldCopyTheHistoryDefensively() {
    // given a mutable list handed to the history
    List<InvocationRecord> records =
        new ArrayList<>(List.of(new InvocationRecord("agent", "search", FINGERPRINT, NOW)));
    Set<String> tools = new HashSet<>(Set.of("search"));
    AgentHistory history = new AgentHistory(records, tools, 1L);

    // when the caller mutates its own collections afterwards
    records.clear();
    tools.clear();

    // then the history is unaffected
    assertEquals(1, history.withinWindow().size());
    assertEquals(Set.of("search"), history.toolsBeforeWindow());
  }

  @Test
  void shouldRejectANegativeBaseline() {
    // given a negative count of previous invocations
    // when constructed
    // then it fails
    assertThrows(IllegalArgumentException.class, () -> new AgentHistory(List.of(), Set.of(), -1L));
  }

  @Test
  void shouldRejectANullHistory() {
    // given no window
    // when constructed
    // then it fails
    assertThrows(NullPointerException.class, () -> new AgentHistory(null, Set.of(), 0L));
  }

  @Test
  void shouldRejectARepeatThresholdBelowTwo() {
    // given a threshold of one
    // when a policy is built
    // then it fails: a single call cannot be a repetition, and such a policy would report every
    // invocation as a loop
    assertThrows(
        IllegalArgumentException.class, () -> new AnomalyPolicy(Duration.ofMinutes(1), 1, 3, 20L));
  }

  @Test
  void shouldRejectANonPositiveWindow() {
    // given a window of zero
    // when a policy is built
    // then it fails: nothing could ever fall inside it
    assertThrows(IllegalArgumentException.class, () -> new AnomalyPolicy(Duration.ZERO, 5, 3, 20L));
  }

  @Test
  void shouldRejectAWindowPointingBackwards() {
    // given a negative window
    // when a policy is built
    // then it fails
    assertThrows(
        IllegalArgumentException.class, () -> new AnomalyPolicy(Duration.ofMinutes(-1), 5, 3, 20L));
  }

  @Test
  void shouldRejectANovelToolThresholdBelowOne() {
    // given a threshold of zero
    // when a policy is built
    // then it fails
    assertThrows(
        IllegalArgumentException.class, () -> new AnomalyPolicy(Duration.ofMinutes(1), 5, 0, 20L));
  }

  @Test
  void shouldRejectANegativeBaselineMinimum() {
    // given a negative minimum
    // when a policy is built
    // then it fails
    assertThrows(
        IllegalArgumentException.class, () -> new AnomalyPolicy(Duration.ofMinutes(1), 5, 3, -1L));
  }

  @Test
  void shouldRejectAPolicyWithoutAWindow() {
    // given no window
    // when a policy is built
    // then it fails
    assertThrows(NullPointerException.class, () -> new AnomalyPolicy(null, 5, 3, 20L));
  }

  @Test
  void shouldDescribeARepetitionSignal() {
    // given a repetition signal
    AnomalySignal signal = new AnomalySignal(AnomalyKind.REPETITION_LOOP, 7, 5, "search");

    // when described
    // then the text names the tool and both counts, and never the arguments
    assertEquals("repetition-loop: 7 identical calls to 'search' (threshold 5)", signal.describe());
  }

  @Test
  void shouldDescribeANovelToolSignal() {
    // given a novel tool signal
    AnomalySignal signal =
        new AnomalySignal(AnomalyKind.NOVEL_TOOL_BURST, 3, 3, "delete_db, http_post, send_email");

    // when described
    // then the text lists the tools
    assertEquals(
        "novel-tool-burst: 3 tools never used before (delete_db, http_post, send_email), "
            + "threshold 3",
        signal.describe());
  }

  @Test
  void shouldRejectASignalThatDidNotReachItsThreshold() {
    // given fewer observations than the threshold
    // when a signal is built
    // then it fails: a signal that did not fire is not a signal, and it would produce an
    // escalation reason contradicting itself
    assertThrows(
        IllegalArgumentException.class,
        () -> new AnomalySignal(AnomalyKind.REPETITION_LOOP, 2, 5, "search"));
  }

  @Test
  void shouldRejectASignalWithABlankSubject() {
    // given no subject
    // when a signal is built
    // then it fails
    assertThrows(
        IllegalArgumentException.class,
        () -> new AnomalySignal(AnomalyKind.REPETITION_LOOP, 5, 5, " "));
  }

  @Test
  void shouldRejectASignalWithAThresholdBelowOne() {
    // given a threshold of zero
    // when a signal is built
    // then it fails
    assertThrows(
        IllegalArgumentException.class,
        () -> new AnomalySignal(AnomalyKind.REPETITION_LOOP, 5, 0, "search"));
  }

  @Test
  void shouldRejectASignalWithoutAKind() {
    // given no kind
    // when a signal is built
    // then it fails
    assertThrows(NullPointerException.class, () -> new AnomalySignal(null, 5, 5, "search"));
  }

  @Test
  void shouldRejectAnAnomalyVerdictWithoutSignals() {
    // given an empty list of signals
    // when a detected verdict is built
    // then it fails: an escalation with no reason is worse than no escalation
    assertThrows(IllegalArgumentException.class, () -> new AnomalyDetected(List.of()));
  }

  @Test
  void shouldRejectANullListOfSignals() {
    // given no signals at all
    // when a detected verdict is built
    // then it fails
    assertThrows(NullPointerException.class, () -> new AnomalyDetected(null));
  }

  @Test
  void shouldExposeOnlyTwoKindsOfVerdict() {
    // given the sealed verdict hierarchy
    // when its permitted subclasses are listed
    // then there are exactly two: an anomaly either exists or it does not, there is no third state
    assertEquals(2, AnomalyVerdict.class.getPermittedSubclasses().length);
    assertTrue(new NoAnomaly() instanceof AnomalyVerdict);
  }
}
