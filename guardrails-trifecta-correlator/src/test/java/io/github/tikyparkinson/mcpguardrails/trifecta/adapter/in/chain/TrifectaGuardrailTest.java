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
package io.github.tikyparkinson.mcpguardrails.trifecta.adapter.in.chain;

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

import io.github.tikyparkinson.mcpguardrails.core.adapter.in.mcp.GuardedToolCallHandler;
import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import io.github.tikyparkinson.mcpguardrails.trifecta.application.port.in.AssessTrifectaUseCase;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.Capability;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.SessionId;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.TrifectaComplete;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.TrifectaIncomplete;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrifectaGuardrailTest {

  private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");
  private static final Set<Capability> ALL_THREE = Set.of(Capability.values());

  private AssessTrifectaUseCase useCase;
  private TrifectaGuardrail guardrail;

  @BeforeEach
  void setUp() {
    useCase = mock(AssessTrifectaUseCase.class);
    guardrail = new TrifectaGuardrail(useCase, SessionIdResolver.mcpSessionOrAgent());
  }

  @Test
  void shouldAnnounceItsNameAndPositionInTheChain() {
    // given the guardrail
    // when asked to identify itself
    // then it runs after anomaly-detector (80) and before ratelimit (100)
    assertEquals("trifecta-correlator", guardrail.name());
    assertEquals(90, guardrail.order());
  }

  @Test
  void shouldAllowWhenTheThreeLegsHaveNotMet() {
    // given a session still missing a leg
    when(useCase.assess(any(), any(), any()))
        .thenReturn(new TrifectaIncomplete(Set.of(Capability.PRIVATE_DATA)));

    // when the invocation is evaluated
    // then it proceeds: one or two legs is the normal state of most sessions
    assertInstanceOf(Allow.class, guardrail.evaluate(context("read_customer", "sess-A")));
  }

  @Test
  void shouldEscalateWhenTheThreeLegsMeet() {
    // given a session where the trifecta just closed
    when(useCase.assess(any(), any(), any())).thenReturn(new TrifectaComplete(ALL_THREE, true));

    // when the invocation is evaluated
    Escalate escalate =
        assertInstanceOf(Escalate.class, guardrail.evaluate(context("send_email", "sess-A")));

    // then the reason names the three legs in a stable order and says this call closed it
    assertEquals(
        "lethal trifecta active in this session (private data, untrusted content, external comms);"
            + " closed by this invocation",
        escalate.reason());
  }

  @Test
  void shouldEscalateWithoutClaimingAClosureWhenTheTrifectaWasAlreadyOpen() {
    // given a session whose trifecta closed earlier
    when(useCase.assess(any(), any(), any())).thenReturn(new TrifectaComplete(ALL_THREE, false));

    // when a later invocation is evaluated
    Escalate escalate =
        assertInstanceOf(Escalate.class, guardrail.evaluate(context("get_time", "sess-A")));

    // then it still escalates, but the reason does not misreport what just happened
    assertEquals(
        "lethal trifecta active in this session (private data, untrusted content, external comms)",
        escalate.reason());
  }

  @Test
  void shouldKeepEscalatingForAToolThatTouchesNothing() {
    // given a session with the trifecta already closed
    when(useCase.assess(any(), any(), any())).thenReturn(new TrifectaComplete(ALL_THREE, false));

    // when a harmless tool is invoked
    // then it escalates too: what is compromised is the session, not the individual call
    assertInstanceOf(Escalate.class, guardrail.evaluate(context("get_time", "sess-A")));
  }

  @Test
  void shouldNeverDeny() {
    // given both possible complete verdicts
    for (boolean closedNow : new boolean[] {true, false}) {
      when(useCase.assess(any(), any(), any()))
          .thenReturn(new TrifectaComplete(ALL_THREE, closedNow));

      // when the invocation is evaluated
      GuardrailDecision decision = guardrail.evaluate(context("send_email", "sess-A"));

      // then it escalates. Plenty of legitimate sessions meet all three — read a ticket, look up a
      // customer, reply by email — so denying would break the product rather than protect it
      assertFalse(decision instanceof Deny);
      assertInstanceOf(Escalate.class, decision);
    }
  }

  @Test
  void shouldNameTheLegsInDeclarationOrderRatherThanSetOrder() {
    // given a complete trifecta whose set iterates in whatever order it likes
    when(useCase.assess(any(), any(), any())).thenReturn(new TrifectaComplete(ALL_THREE, false));

    // when the invocation is evaluated twice
    String first = ((Escalate) guardrail.evaluate(context("t", "sess-A"))).reason();
    String second = ((Escalate) guardrail.evaluate(context("t", "sess-A"))).reason();

    // then the reason is reproducible: an operator comparing two incidents must not see a
    // difference that is only the iteration order of a set
    assertEquals(first, second);
    assertTrue(first.indexOf("private data") < first.indexOf("untrusted content"), first);
  }

  @Test
  void shouldCorrelateOnTheTransportSessionWhenThereIsOne() {
    // given an invocation carrying an MCP session
    when(useCase.assess(any(), any(), any()))
        .thenReturn(new TrifectaIncomplete(Set.of(Capability.PRIVATE_DATA)));

    // when it is evaluated
    guardrail.evaluate(context("read_customer", "sess-A"));

    // then the session, not the agent, is what the assessment is keyed on
    verify(useCase).assess(SessionId.ofMcpSession("sess-A"), "read_customer", NOW);
  }

  @Test
  void shouldPassTheInvocationInstantFromTheContext() {
    // given an invocation carrying its own instant
    when(useCase.assess(any(), any(), any()))
        .thenReturn(new TrifectaIncomplete(Set.of(Capability.PRIVATE_DATA)));

    // when it is evaluated
    guardrail.evaluate(context("read_customer", "sess-A"));

    // then session expiry measures from when the call happened, not from a second clock read here
    verify(useCase).assess(any(), any(), eq(NOW));
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
    // when built
    // then it fails at wiring time
    SessionIdResolver resolver = SessionIdResolver.mcpSessionOrAgent();
    assertThrows(NullPointerException.class, () -> new TrifectaGuardrail(null, resolver));
  }

  @Test
  void shouldRejectAGuardrailWithoutASessionResolver() {
    // given no resolver
    // when built
    // then it fails
    assertThrows(NullPointerException.class, () -> new TrifectaGuardrail(useCase, null));
  }

  private static ToolInvocationContext context(String tool, String sessionId) {
    return new ToolInvocationContext(
        new AgentId("copilot"),
        new ToolName(tool),
        NOW,
        Map.of(),
        Map.of(GuardedToolCallHandler.SESSION_ID, sessionId));
  }
}
