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
package io.github.tikyparkinson.mcpguardrails.approval.adapter.in.escalation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.approval.application.port.in.RequestApprovalUseCase;
import io.github.tikyparkinson.mcpguardrails.approval.domain.Approved;
import io.github.tikyparkinson.mcpguardrails.approval.domain.Rejected;
import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.ApprovedExecution;
import io.github.tikyparkinson.mcpguardrails.core.domain.ChainVerdict;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailEvaluation;
import io.github.tikyparkinson.mcpguardrails.core.domain.RejectedExecution;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApprovalGateTest {

  private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");
  private static final Map<String, Object> ARGUMENTS = Map.of("table", "prod");
  private static final String REASON = "anomalous agent behaviour";

  private RequestApprovalUseCase useCase;
  private ApprovalGate gate;

  @BeforeEach
  void setUp() {
    useCase = mock(RequestApprovalUseCase.class);
    gate = new ApprovalGate(useCase);
  }

  @Test
  void shouldApproveExecutionWhenAPersonApproves() {
    // given an approval from a person
    when(useCase.requestApproval(any(), any(), any(), any(), any()))
        .thenReturn(new Approved("alice"));

    // when the escalation is resolved
    ApprovedExecution outcome =
        assertInstanceOf(ApprovedExecution.class, gate.resolve(context(), escalated()));

    // then the approver travels with the outcome, so the execution is attributable
    assertEquals("alice", outcome.approvedBy());
  }

  @Test
  void shouldRejectExecutionWhenAPersonRefuses() {
    // given a refusal from a person
    when(useCase.requestApproval(any(), any(), any(), any(), any()))
        .thenReturn(new Rejected("bob", "not on prod"));

    // when the escalation is resolved
    RejectedExecution outcome =
        assertInstanceOf(RejectedExecution.class, gate.resolve(context(), escalated()));

    // then the reason names both the motive and who gave it
    assertEquals("not on prod (by bob)", outcome.reason());
  }

  @Test
  void shouldRejectExecutionWhenNobodyAnswers() {
    // given a deadline that passed
    when(useCase.requestApproval(any(), any(), any(), any(), any()))
        .thenReturn(Rejected.byTimeout(Duration.ofMinutes(2)));

    // when the escalation is resolved
    RejectedExecution outcome =
        assertInstanceOf(RejectedExecution.class, gate.resolve(context(), escalated()));

    // then silence blocks the invocation, and the reason says it was nobody's decision
    assertTrue(outcome.reason().contains("no approval within"), outcome.reason());
    assertTrue(outcome.reason().contains(Rejected.SYSTEM), outcome.reason());
  }

  @Test
  void shouldPassTheInvocationDetailsToTheUseCase() {
    // given an escalated invocation
    when(useCase.requestApproval(any(), any(), any(), any(), any()))
        .thenReturn(new Approved("alice"));

    // when the escalation is resolved
    gate.resolve(context(), escalated());

    // then everything a person needs is forwarded, including the instant from the context rather
    // than a clock read here: the request is dated when the call happened
    verify(useCase).requestApproval("agent-1", "delete_table", ARGUMENTS, REASON, NOW);
  }

  @Test
  void shouldForwardTheReasonOfTheEscalationThatWon() {
    // given a chain where one guardrail allowed and another escalated
    when(useCase.requestApproval(any(), any(), any(), any(), any()))
        .thenReturn(new Approved("alice"));

    // when the escalation is resolved
    gate.resolve(context(), escalated());

    // then the motive shown is the escalation's own, not a summary invented here
    verify(useCase).requestApproval(any(), any(), any(), eq(REASON), any());
  }

  @Test
  void shouldFailWhenReachedWithAnAllowedVerdict() {
    // given a verdict that never should have got here
    ChainVerdict allowed = new ChainVerdict(new Allow(), List.of());

    // when the gate is asked to resolve it
    // then it fails loudly: a resolver is only consulted on an escalation, so anything else means
    // the handler is miswired, and inventing a motive for a person to read would hide that
    assertThrows(IllegalArgumentException.class, () -> gate.resolve(context(), allowed));
  }

  @Test
  void shouldFailWhenReachedWithADeniedVerdict() {
    // given a denied verdict
    ChainVerdict denied = new ChainVerdict(new Deny("blocked"), List.of());

    // when the gate is asked to resolve it
    // then it fails rather than asking a person to approve something already denied
    assertThrows(IllegalArgumentException.class, () -> gate.resolve(context(), denied));
  }

  @Test
  void shouldRejectResolvingWithoutAContext() {
    // given no context
    ChainVerdict verdict = escalated();

    // when resolved
    // then it fails
    assertThrows(NullPointerException.class, () -> gate.resolve(null, verdict));
  }

  @Test
  void shouldRejectResolvingWithoutAVerdict() {
    // given no verdict
    ToolInvocationContext context = context();

    // when resolved
    // then it fails
    assertThrows(NullPointerException.class, () -> gate.resolve(context, null));
  }

  @Test
  void shouldRejectAGateWithoutAUseCase() {
    // given no use case
    // when the gate is built
    // then it fails at wiring time rather than on the first escalation
    assertThrows(NullPointerException.class, () -> new ApprovalGate(null));
  }

  private static ToolInvocationContext context() {
    return new ToolInvocationContext(
        new AgentId("agent-1"), new ToolName("delete_table"), NOW, ARGUMENTS, Map.of());
  }

  private static ChainVerdict escalated() {
    return new ChainVerdict(
        new Escalate(REASON),
        List.of(
            new GuardrailEvaluation("authz", new Allow()),
            new GuardrailEvaluation("anomaly-detector", new Escalate(REASON))));
  }
}
