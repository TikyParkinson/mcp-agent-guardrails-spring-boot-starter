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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AnomalyAnalyzerTest {

  private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");
  private static final AnomalyPolicy POLICY = new AnomalyPolicy(Duration.ofMinutes(1), 5, 3, 20L);
  private static final ArgumentsFingerprint SAME = ArgumentsFingerprint.of(Map.of("q", "x"));
  private static final ArgumentsFingerprint OTHER = ArgumentsFingerprint.of(Map.of("q", "y"));
  private static final Set<String> KNOWN_TOOLS = Set.of("search");
  private static final long LONG_BASELINE = 50L;

  @Test
  void shouldReportNoAnomalyWhenTheWindowIsEmpty() {
    // given an agent that has done nothing inside the window
    AgentHistory history = new AgentHistory(List.of(), KNOWN_TOOLS, LONG_BASELINE);

    // when analysed
    // then nothing is reported
    assertInstanceOf(NoAnomaly.class, AnomalyAnalyzer.analyze(history, POLICY));
  }

  @Test
  void shouldReportALoopWhenIdenticalCallsReachTheThreshold() {
    // given exactly as many identical calls as the threshold
    AgentHistory history = new AgentHistory(repeat("search", SAME, 5), KNOWN_TOOLS, LONG_BASELINE);

    // when analysed
    // then a repetition signal names the tool and the count
    assertEquals(
        List.of(new AnomalySignal(AnomalyKind.REPETITION_LOOP, 5, 5, "search")),
        signalsOf(AnomalyAnalyzer.analyze(history, POLICY)));
  }

  @Test
  void shouldReportNoLoopOneCallBelowTheThreshold() {
    // given one call fewer than the threshold
    AgentHistory history = new AgentHistory(repeat("search", SAME, 4), KNOWN_TOOLS, LONG_BASELINE);

    // when analysed
    // then nothing fires: the boundary is inclusive on the threshold itself
    assertInstanceOf(NoAnomaly.class, AnomalyAnalyzer.analyze(history, POLICY));
  }

  @Test
  void shouldNotReportALoopWhenTheSameToolIsCalledWithDifferentArguments() {
    // given many calls to one tool, each with different arguments
    List<InvocationRecord> records = new ArrayList<>();
    for (int index = 0; index < 10; index++) {
      records.add(
          new InvocationRecord(
              "agent", "search", ArgumentsFingerprint.of(Map.of("page", index)), NOW));
    }
    AgentHistory history = new AgentHistory(records, KNOWN_TOOLS, LONG_BASELINE);

    // when analysed
    // then no loop is reported: paging through results is progress, not repetition
    assertInstanceOf(NoAnomaly.class, AnomalyAnalyzer.analyze(history, POLICY));
  }

  @Test
  void shouldNotReportALoopWhenTheSameArgumentsGoToDifferentTools() {
    // given the same arguments sent to several different tools
    List<InvocationRecord> records = new ArrayList<>();
    for (int index = 0; index < 10; index++) {
      records.add(new InvocationRecord("agent", "tool" + index, SAME, NOW));
    }
    AgentHistory history = new AgentHistory(records, KNOWN_TOOLS, 0L);

    // when analysed
    // then no loop is reported: repetition is per tool and arguments together
    assertInstanceOf(NoAnomaly.class, AnomalyAnalyzer.analyze(history, POLICY));
  }

  @Test
  void shouldIgnoreRecordsWithoutAKnownFingerprint() {
    // given many repeated calls whose arguments the history source could not supply
    AgentHistory history =
        new AgentHistory(
            repeat("search", ArgumentsFingerprint.unknown(), 20), KNOWN_TOOLS, LONG_BASELINE);

    // when analysed
    // then no loop is reported: treating unknown as a value would make every such record look
    // identical to every other and fire on any agent bridged onto the audit log
    assertInstanceOf(NoAnomaly.class, AnomalyAnalyzer.analyze(history, POLICY));
  }

  @Test
  void shouldReportTheLargestGroupWhenSeveralRepeat() {
    // given two repeated groups of different sizes, both past the threshold
    List<InvocationRecord> records = new ArrayList<>(repeat("small", SAME, 5));
    records.addAll(repeat("big", OTHER, 8));
    AgentHistory history = new AgentHistory(records, KNOWN_TOOLS, LONG_BASELINE);

    // when analysed
    // then the reason cites the worst offender
    assertEquals(
        new AnomalySignal(AnomalyKind.REPETITION_LOOP, 8, 5, "big"),
        signalsOf(AnomalyAnalyzer.analyze(history, POLICY)).getFirst());
  }

  @Test
  void shouldReportABurstWhenEnoughNeverSeenToolsAppear() {
    // given three tools the agent had never used, with a long baseline behind it
    AgentHistory history =
        new AgentHistory(
            List.of(
                new InvocationRecord("agent", "send_email", SAME, NOW),
                new InvocationRecord("agent", "delete_db", OTHER, NOW),
                new InvocationRecord("agent", "http_post", SAME, NOW)),
            KNOWN_TOOLS,
            LONG_BASELINE);

    // when analysed
    // then a burst signal lists the tools in a stable alphabetical order, so the same behaviour
    // always produces the same reason
    assertEquals(
        new AnomalySignal(AnomalyKind.NOVEL_TOOL_BURST, 3, 3, "delete_db, http_post, send_email"),
        signalsOf(AnomalyAnalyzer.analyze(history, POLICY)).getFirst());
  }

  @Test
  void shouldReportNoBurstOneToolBelowTheThreshold() {
    // given two never-seen tools when three are required
    AgentHistory history =
        new AgentHistory(
            List.of(
                new InvocationRecord("agent", "send_email", SAME, NOW),
                new InvocationRecord("agent", "delete_db", OTHER, NOW)),
            KNOWN_TOOLS,
            LONG_BASELINE);

    // when analysed
    // then nothing fires
    assertInstanceOf(NoAnomaly.class, AnomalyAnalyzer.analyze(history, POLICY));
  }

  @Test
  void shouldStayQuietOnANewAgentWithoutABaseline() {
    // given an agent whose whole history is shorter than the required baseline
    AgentHistory history =
        new AgentHistory(
            List.of(
                new InvocationRecord("agent", "a", SAME, NOW),
                new InvocationRecord("agent", "b", OTHER, NOW),
                new InvocationRecord("agent", "c", SAME, NOW)),
            Set.of(),
            3L);

    // when analysed
    // then no burst is reported: during the first minute of any agent every tool is new, and
    // firing here would flag every healthy start-up
    assertInstanceOf(NoAnomaly.class, AnomalyAnalyzer.analyze(history, POLICY));
  }

  @Test
  void shouldReportABurstAsSoonAsTheBaselineIsLongEnough() {
    // given a baseline exactly at the configured minimum
    AgentHistory history =
        new AgentHistory(
            List.of(
                new InvocationRecord("agent", "a", SAME, NOW),
                new InvocationRecord("agent", "b", OTHER, NOW),
                new InvocationRecord("agent", "c", SAME, NOW)),
            KNOWN_TOOLS,
            20L);

    // when analysed
    // then the heuristic speaks: the boundary is inclusive
    assertEquals(
        new AnomalySignal(AnomalyKind.NOVEL_TOOL_BURST, 3, 3, "a, b, c"),
        signalsOf(AnomalyAnalyzer.analyze(history, POLICY)).getFirst());
  }

  @Test
  void shouldCountEachNovelToolOnceHoweverOftenItIsCalled() {
    // given two novel tools, one of them called many times
    List<InvocationRecord> records = new ArrayList<>(repeat("new_one", SAME, 9));
    records.add(new InvocationRecord("agent", "new_two", OTHER, NOW));
    AgentHistory history = new AgentHistory(records, KNOWN_TOOLS, LONG_BASELINE);

    // when analysed
    // then only the repetition fires: the burst counts distinct tools, not invocations
    assertEquals(
        List.of(new AnomalySignal(AnomalyKind.REPETITION_LOOP, 9, 5, "new_one")),
        signalsOf(AnomalyAnalyzer.analyze(history, POLICY)));
  }

  @Test
  void shouldReportBothSignalsWhenBothHeuristicsFire() {
    // given an agent both looping and sweeping across new tools
    List<InvocationRecord> records = new ArrayList<>(repeat("search", SAME, 5));
    records.add(new InvocationRecord("agent", "x", OTHER, NOW));
    records.add(new InvocationRecord("agent", "y", OTHER, NOW));
    records.add(new InvocationRecord("agent", "z", OTHER, NOW));
    AgentHistory history = new AgentHistory(records, KNOWN_TOOLS, LONG_BASELINE);

    // when analysed
    // then one verdict carries both signals: the heuristics are independent and neither masks the
    // other
    assertEquals(
        List.of(
            new AnomalySignal(AnomalyKind.REPETITION_LOOP, 5, 5, "search"),
            new AnomalySignal(AnomalyKind.NOVEL_TOOL_BURST, 3, 3, "x, y, z")),
        signalsOf(AnomalyAnalyzer.analyze(history, POLICY)));
  }

  @Test
  void shouldRejectAnalysisWithoutAHistory() {
    // given no history
    // when analysed
    // then it fails rather than reporting no anomaly, which would look like a clean agent
    assertThrows(NullPointerException.class, () -> AnomalyAnalyzer.analyze(null, POLICY));
  }

  @Test
  void shouldRejectAnalysisWithoutAPolicy() {
    // given no policy
    // when analysed
    // then it fails
    assertThrows(
        NullPointerException.class,
        () -> AnomalyAnalyzer.analyze(new AgentHistory(List.of(), Set.of(), 0L), null));
  }

  private static List<InvocationRecord> repeat(
      String tool, ArgumentsFingerprint fingerprint, int times) {
    List<InvocationRecord> records = new ArrayList<>(times);
    for (int index = 0; index < times; index++) {
      records.add(new InvocationRecord("agent", tool, fingerprint, NOW));
    }
    return records;
  }

  private static List<AnomalySignal> signalsOf(AnomalyVerdict verdict) {
    return assertInstanceOf(AnomalyDetected.class, verdict).signals();
  }
}
