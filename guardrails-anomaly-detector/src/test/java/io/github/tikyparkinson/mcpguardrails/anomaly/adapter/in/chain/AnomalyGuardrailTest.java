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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.anomaly.application.port.in.DetectAnomalyUseCase;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.AnomalyDetected;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.AnomalyKind;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.AnomalySignal;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.NoAnomaly;
import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AnomalyGuardrailTest {

  private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");

  private DetectAnomalyUseCase useCase;
  private AnomalyGuardrail guardrail;

  @BeforeEach
  void setUp() {
    useCase = mock(DetectAnomalyUseCase.class);
    guardrail = new AnomalyGuardrail(useCase);
  }

  @Test
  void shouldAnnounceItsNameAndPositionInTheChain() {
    // given the guardrail
    // when asked to identify itself
    // then it runs after egress-control (70) and before ratelimit (100)
    assertEquals("anomaly-detector", guardrail.name());
    assertEquals(80, guardrail.order());
  }

  @Test
  void shouldAllowWhenNoAnomalyIsReported() {
    // given a clean verdict
    when(useCase.inspect(any(), any(), any(), any())).thenReturn(new NoAnomaly());

    // when the invocation is evaluated
    // then the call proceeds
    assertInstanceOf(Allow.class, guardrail.evaluate(context("search", Map.of("q", "x"))));
  }

  @Test
  void shouldEscalateWhenAnAnomalyIsReported() {
    // given a loop verdict
    when(useCase.inspect(any(), any(), any(), any()))
        .thenReturn(
            new AnomalyDetected(
                List.of(new AnomalySignal(AnomalyKind.REPETITION_LOOP, 7, 5, "search"))));

    // when the invocation is evaluated
    // then it escalates and the reason describes the signal
    Escalate escalate =
        assertInstanceOf(Escalate.class, guardrail.evaluate(context("search", Map.of("q", "x"))));
    assertEquals(
        "anomalous agent behaviour (repetition-loop: 7 identical calls to 'search' (threshold 5))",
        escalate.reason());
  }

  @Test
  void shouldJoinEverySignalIntoOneReason() {
    // given both heuristics firing at once
    when(useCase.inspect(any(), any(), any(), any()))
        .thenReturn(
            new AnomalyDetected(
                List.of(
                    new AnomalySignal(AnomalyKind.REPETITION_LOOP, 5, 5, "search"),
                    new AnomalySignal(AnomalyKind.NOVEL_TOOL_BURST, 3, 3, "x, y, z"))));

    // when the invocation is evaluated
    // then a single reason carries both, separated by a semicolon
    Escalate escalate =
        assertInstanceOf(Escalate.class, guardrail.evaluate(context("search", Map.of("q", "x"))));
    assertEquals(
        "anomalous agent behaviour (repetition-loop: 5 identical calls to 'search' (threshold 5); "
            + "novel-tool-burst: 3 tools never used before (x, y, z), threshold 3)",
        escalate.reason());
  }

  @Test
  void shouldNeverDeny() {
    // given an anomaly of every kind this guardrail can report
    for (AnomalyKind kind : AnomalyKind.values()) {
      when(useCase.inspect(any(), any(), any(), any()))
          .thenReturn(new AnomalyDetected(List.of(new AnomalySignal(kind, 9, 3, "subject"))));

      // when the invocation is evaluated
      GuardrailDecision decision = guardrail.evaluate(context("search", Map.of("q", "x")));

      // then the outcome is an escalation: a threshold heuristic is also reached by a legitimate
      // retry with backoff, and blocking on that produces incidents nobody can reproduce
      assertInstanceOf(Escalate.class, decision);
    }
  }

  @Test
  void shouldNotPutArgumentValuesInTheReason() {
    // given a loop on a call carrying a secret
    when(useCase.inspect(any(), any(), any(), any()))
        .thenReturn(
            new AnomalyDetected(
                List.of(new AnomalySignal(AnomalyKind.REPETITION_LOOP, 5, 5, "login"))));

    // when the invocation is evaluated
    Escalate escalate =
        assertInstanceOf(
            Escalate.class, guardrail.evaluate(context("login", Map.of("password", "hunter2"))));

    // then the reason names the tool but not the value: the reason travels back to the model
    assertTrue(escalate.reason().contains("login"));
    assertFalse(escalate.reason().contains("hunter2"));
  }

  @Test
  void shouldPassTheInvocationInstantFromTheContext() {
    // given an invocation carrying its own instant
    when(useCase.inspect(any(), any(), any(), any())).thenReturn(new NoAnomaly());

    // when the invocation is evaluated
    guardrail.evaluate(context("search", Map.of("q", "x")));

    // then that instant is what the analysis uses, rather than a second clock read here
    ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
    verify(useCase).inspect(eq("agent-1"), eq("search"), any(), captor.capture());
    assertEquals(NOW, captor.getValue());
  }

  @Test
  void shouldRejectAnEvaluationWithoutAContext() {
    // given no context
    // when evaluated
    // then it fails
    assertThrows(NullPointerException.class, () -> guardrail.evaluate(null));
  }

  @Test
  void shouldRejectAGuardrailWithoutAUseCase() {
    // given no use case
    // when the guardrail is built
    // then it fails at wiring time
    assertThrows(NullPointerException.class, () -> new AnomalyGuardrail(null));
  }

  private static ToolInvocationContext context(String tool, Map<String, Object> arguments) {
    return new ToolInvocationContext(
        new AgentId("agent-1"), new ToolName(tool), NOW, arguments, Map.of());
  }
}
