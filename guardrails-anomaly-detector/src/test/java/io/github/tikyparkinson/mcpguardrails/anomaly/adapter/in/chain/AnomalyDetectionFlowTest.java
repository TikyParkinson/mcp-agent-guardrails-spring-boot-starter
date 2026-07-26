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
package io.github.tikyparkinson.mcpguardrails.anomaly.adapter.in.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.anomaly.adapter.out.history.InMemoryInvocationHistoryAdapter;
import io.github.tikyparkinson.mcpguardrails.anomaly.application.usecase.DetectAnomalyService;
import io.github.tikyparkinson.mcpguardrails.anomaly.infrastructure.GuardrailsAnomalyProperties;
import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The guardrail wired to the real in-memory history, as the starter would assemble it. */
class AnomalyDetectionFlowTest {

  private static final Instant START = Instant.parse("2026-07-26T10:00:00Z");
  private static final GuardrailsAnomalyProperties DEFAULTS = new GuardrailsAnomalyProperties();

  @Test
  void shouldEscalateOnTheCallThatReachesTheRepeatThreshold() {
    // given an agent repeating the same call
    AnomalyGuardrail guardrail = wiring(DEFAULTS);

    // when it makes the call five times inside the window
    for (int call = 1; call < DEFAULTS.repeatThreshold(); call++) {
      assertInstanceOf(Allow.class, guardrail.evaluate(context("search", Map.of("q", "x"), call)));
    }
    GuardrailDecision fifth =
        guardrail.evaluate(context("search", Map.of("q", "x"), DEFAULTS.repeatThreshold()));

    // then the call that reaches the threshold is the one stopped, not the one after it: the
    // service records before it analyses
    assertInstanceOf(Escalate.class, fifth);
  }

  @Test
  void shouldAllowARepetitionSpreadBeyondTheWindow() {
    // given an agent repeating the same call once every thirty seconds
    AnomalyGuardrail guardrail = wiring(DEFAULTS);

    // when ten such calls are spread over five minutes
    GuardrailDecision last = null;
    for (int call = 0; call < 10; call++) {
      last = guardrail.evaluate(context("search", Map.of("q", "x"), call * 30));
    }

    // then nothing fires: the window only ever holds two of them, and a slow poll is not a loop
    assertInstanceOf(Allow.class, last);
  }

  @Test
  void shouldAllowARepetitionByADifferentAgent() {
    // given one agent already at the threshold
    AnomalyGuardrail guardrail = wiring(DEFAULTS);
    for (int call = 1; call <= DEFAULTS.repeatThreshold(); call++) {
      guardrail.evaluate(context("search", Map.of("q", "x"), call));
    }

    // when a second agent makes its first call
    GuardrailDecision decision =
        guardrail.evaluate(
            new ToolInvocationContext(
                new AgentId("agent-2"),
                new ToolName("search"),
                START.plusSeconds(6),
                Map.of("q", "x"),
                Map.of()));

    // then it is allowed: history is per agent
    assertInstanceOf(Allow.class, decision);
  }

  @Test
  void shouldEscalateWhenAnEstablishedAgentSweepsAcrossNewTools() {
    // given an agent with a long baseline of one familiar tool
    AnomalyGuardrail guardrail = wiring(DEFAULTS);
    for (int call = 0; call < 25; call++) {
      guardrail.evaluate(context("search", Map.of("page", call), call));
    }

    // when it suddenly reaches for three tools it never used, well after that baseline
    guardrail.evaluate(context("read_env", Map.of("a", 1), 600));
    guardrail.evaluate(context("list_keys", Map.of("a", 2), 601));
    GuardrailDecision third = guardrail.evaluate(context("http_post", Map.of("a", 3), 602));

    // then it escalates, naming the tools in a stable order
    Escalate escalate = assertInstanceOf(Escalate.class, third);
    assertEquals(
        "anomalous agent behaviour (novel-tool-burst: 3 tools never used before "
            + "(http_post, list_keys, read_env), threshold 3)",
        escalate.reason());
  }

  @Test
  void shouldStayQuietWhenANewAgentUsesThreeToolsForTheFirstTime() {
    // given an agent with no history at all
    AnomalyGuardrail guardrail = wiring(DEFAULTS);

    // when its first three calls go to three different tools
    guardrail.evaluate(context("read_env", Map.of("a", 1), 0));
    guardrail.evaluate(context("list_keys", Map.of("a", 2), 1));
    GuardrailDecision third = guardrail.evaluate(context("http_post", Map.of("a", 3), 2));

    // then nothing fires: without a baseline every tool is new, and firing here would flag every
    // healthy start-up
    assertInstanceOf(Allow.class, third);
  }

  @Test
  void shouldKeepDetectingAfterTheRecordCapHasFoldedTheBaseline() {
    // given a cap far smaller than the number of tools the agent enumerates
    GuardrailsAnomalyProperties properties =
        new GuardrailsAnomalyProperties(
            true, Duration.ofMinutes(1), 5, 3, 20L, Duration.ofMinutes(30), 10);
    AnomalyGuardrail guardrail = wiring(properties);
    for (int call = 0; call < 40; call++) {
      guardrail.evaluate(context("tool" + call, Map.of("a", call), call));
    }

    // when it then loops on one call, long after the cap folded its early history away
    GuardrailDecision last = null;
    for (int call = 0; call < 5; call++) {
      last = guardrail.evaluate(context("search", Map.of("q", "x"), 600 + call));
    }

    // then detection still works: the folded records kept the baseline alive instead of leaving
    // the agent looking like a newcomer
    Escalate escalate = assertInstanceOf(Escalate.class, last);
    assertTrue(escalate.reason().contains("repetition-loop"), escalate.reason());
  }

  private static AnomalyGuardrail wiring(GuardrailsAnomalyProperties properties) {
    return new AnomalyGuardrail(
        new DetectAnomalyService(
            new InMemoryInvocationHistoryAdapter(
                properties.retention(), properties.maxRecordsPerAgent()),
            properties.toPolicy()));
  }

  private static ToolInvocationContext context(
      String tool, Map<String, Object> arguments, int secondsIn) {
    return new ToolInvocationContext(
        new AgentId("agent-1"),
        new ToolName(tool),
        START.plusSeconds(secondsIn),
        arguments,
        Map.of());
  }
}
