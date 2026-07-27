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
package io.github.tikyparkinson.mcpguardrails.starter.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.audit.application.port.in.RecordAuditEventUseCase;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEvent;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEventType;
import io.github.tikyparkinson.mcpguardrails.audit.domain.NewAuditEvent;
import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolInvocationUseCase;
import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.ChainVerdict;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailEvaluation;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The inbound half of the audit coverage required by VALIDATION-0.2.0.md. */
class AuditingEvaluateToolInvocationTest {

  private static final ToolInvocationContext CONTEXT =
      new ToolInvocationContext(
          new AgentId("agent-1"),
          new ToolName("search"),
          Instant.parse("2026-07-27T10:00:00Z"),
          Map.of(),
          Map.of());

  private final RecordingBus bus = new RecordingBus();

  @Test
  void shouldRecordEveryGuardrailWhenTheChainRuns() {
    // given a chain where three guardrails decided, two of them permissively
    EvaluateToolInvocationUseCase chain =
        context ->
            new ChainVerdict(
                new Deny("outside allowlist"),
                List.of(
                    new GuardrailEvaluation("authz", new Allow("rule[0]")),
                    new GuardrailEvaluation("injection-guard", new Allow()),
                    new GuardrailEvaluation("egress-control", new Deny("outside allowlist"))));

    // when the auditing decorator evaluates
    new AuditingEvaluateToolInvocation(chain, bus).evaluate(CONTEXT);

    // then all three are in the trail, not only the one that blocked. An audit log that records
    // denials alone cannot tell "the guardrail permitted" from "the guardrail never ran"
    assertEquals(
        List.of("authz", "injection-guard", "egress-control"),
        bus.events.stream().map(NewAuditEvent::emittedBy).toList());
  }

  @Test
  void shouldNameTheDecidingGuardrailWhenRecording() {
    // given a guardrail that denied
    EvaluateToolInvocationUseCase chain =
        context ->
            new ChainVerdict(
                new Deny("secret in arguments"),
                List.of(
                    new GuardrailEvaluation("credential-leak", new Deny("secret in arguments"))));

    // when the decorator records it
    new AuditingEvaluateToolInvocation(chain, bus).evaluate(CONTEXT);

    // then the event names the guardrail, never the decorator: whoever reads the trail wants to
    // know who decided, and where the recording happens is an implementation detail
    assertEquals("credential-leak", bus.events.get(0).emittedBy());
  }

  @Test
  void shouldMapEachDecisionToItsEventTypeWhenRecording() {
    // given one guardrail per branch of GuardrailDecision
    EvaluateToolInvocationUseCase chain =
        context ->
            new ChainVerdict(
                new Deny("blocked"),
                List.of(
                    new GuardrailEvaluation("a", new Allow()),
                    new GuardrailEvaluation("b", new Deny("blocked")),
                    new GuardrailEvaluation("c", new Escalate("needs a human"))));

    // when the decorator records them
    new AuditingEvaluateToolInvocation(chain, bus).evaluate(CONTEXT);

    // then each branch has its own type
    assertEquals(
        List.of(
            AuditEventType.DECISION_ALLOW,
            AuditEventType.DECISION_DENY,
            AuditEventType.DECISION_ESCALATE),
        bus.events.stream().map(NewAuditEvent::type).toList());
  }

  @Test
  void shouldCarryTheReasonOfAPermissiveDecisionWhenRecording() {
    // given a guardrail that permitted because of a specific rule
    EvaluateToolInvocationUseCase chain =
        context ->
            new ChainVerdict(
                new Allow(), List.of(new GuardrailEvaluation("authz", new Allow("rule[0]"))));

    // when the decorator records it
    new AuditingEvaluateToolInvocation(chain, bus).evaluate(CONTEXT);

    // then the rule reaches the log. This is the whole point of giving Allow a reason: an audited
    // Allow with an empty detail says the call was permitted but not by which rule
    assertEquals("rule[0]", bus.events.get(0).detail());
  }

  @Test
  void shouldSkipTheAuditGuardrailOwnDecisionWhenRecording() {
    // given the audit guardrail, which always permits, alongside a real one
    EvaluateToolInvocationUseCase chain =
        context ->
            new ChainVerdict(
                new Allow(),
                List.of(
                    new GuardrailEvaluation("audit", new Allow()),
                    new GuardrailEvaluation("authz", new Allow("default"))));

    // when the decorator records
    new AuditingEvaluateToolInvocation(chain, bus).evaluate(CONTEXT);

    // then only the real one is recorded: the audit guardrail already emitted TOOL_INVOKED, and
    // its unconditional Allow would repeat after every single invocation saying nothing
    assertEquals(List.of("authz"), bus.events.stream().map(NewAuditEvent::emittedBy).toList());
  }

  @Test
  void shouldReturnTheVerdictUntouchedWhenRecording() {
    // given a verdict from the underlying chain
    ChainVerdict original =
        new ChainVerdict(new Allow(), List.of(new GuardrailEvaluation("authz", new Allow())));

    // when the decorator evaluates
    ChainVerdict returned =
        new AuditingEvaluateToolInvocation(context -> original, bus).evaluate(CONTEXT);

    // then it is the very same verdict: auditing observes, it never decides
    assertSame(original, returned);
  }

  @Test
  void shouldStillAllowTheCallWhenTheAuditBusFails() {
    // given an audit bus that is down
    RecordAuditEventUseCase broken =
        draft -> {
          throw new IllegalStateException("audit store down");
        };
    ChainVerdict permitted =
        new ChainVerdict(new Allow(), List.of(new GuardrailEvaluation("authz", new Allow())));

    // when the decorator evaluates
    ChainVerdict returned =
        new AuditingEvaluateToolInvocation(context -> permitted, broken).evaluate(CONTEXT);

    // then the invocation still goes through. A broken audit store degrades observability, never
    // protection — failing closed here would make it a single point of failure for the server
    assertSame(permitted, returned);
  }

  @Test
  void shouldRecordNothingWhenTheChainHasNoGuardrails() {
    // given a chain with nothing registered
    EvaluateToolInvocationUseCase empty = context -> new ChainVerdict(new Allow(), List.of());

    // when the decorator evaluates
    new AuditingEvaluateToolInvocation(empty, bus).evaluate(CONTEXT);

    // then nothing is written
    assertTrue(bus.events.isEmpty());
  }

  @Test
  void shouldCopyAgentAndToolFromTheContextWhenRecording() {
    // given any decision
    EvaluateToolInvocationUseCase chain =
        context ->
            new ChainVerdict(new Allow(), List.of(new GuardrailEvaluation("authz", new Allow())));

    // when the decorator records it
    new AuditingEvaluateToolInvocation(chain, bus).evaluate(CONTEXT);

    // then the event identifies the invocation
    NewAuditEvent event = bus.events.get(0);
    assertEquals("agent-1", event.agentId());
    assertEquals("search", event.toolName());
  }

  @Test
  void shouldNeverRecordTheArgumentsWhenRecording() {
    // given an invocation whose arguments carry a secret
    ToolInvocationContext withSecret =
        new ToolInvocationContext(
            new AgentId("agent-1"),
            new ToolName("store_note"),
            Instant.parse("2026-07-27T10:00:00Z"),
            Map.of("text", "AKIAIOSFODNN7EXAMPLE"),
            Map.of());
    EvaluateToolInvocationUseCase chain =
        context ->
            new ChainVerdict(
                new Deny("credential detected"),
                List.of(
                    new GuardrailEvaluation("credential-leak", new Deny("credential detected"))));

    // when the decorator records the denial
    new AuditingEvaluateToolInvocation(chain, bus).evaluate(withSecret);

    // then the secret is not in the trail. guardrails-audit deliberately stores no arguments, and
    // the decorator must not smuggle them in through the detail
    assertFalse(bus.events.get(0).detail().contains("AKIAIOSFODNN7EXAMPLE"));
  }

  @Test
  void shouldRejectNullCollaboratorsWhenConstructed() {
    // given
    EvaluateToolInvocationUseCase chain = context -> new ChainVerdict(new Allow(), List.of());

    // when / then
    assertThrows(NullPointerException.class, () -> new AuditingEvaluateToolInvocation(null, bus));
    assertThrows(NullPointerException.class, () -> new AuditingEvaluateToolInvocation(chain, null));
  }

  /** Captures drafts so a test can assert on what would have been persisted. */
  private static final class RecordingBus implements RecordAuditEventUseCase {
    private final List<NewAuditEvent> events = new ArrayList<>();

    @Override
    public AuditEvent publish(NewAuditEvent draft) {
      events.add(draft);
      return new AuditEvent(
          UUID.randomUUID(),
          draft.agentId(),
          draft.toolName(),
          Instant.parse("2026-07-27T10:00:00Z"),
          draft.emittedBy(),
          draft.type(),
          draft.detail());
    }
  }
}
