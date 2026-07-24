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
package io.github.tikyparkinson.mcpguardrails.authz.adapter.in.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.audit.application.port.in.RecordAuditEventUseCase;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEventType;
import io.github.tikyparkinson.mcpguardrails.audit.domain.NewAuditEvent;
import io.github.tikyparkinson.mcpguardrails.authz.application.port.in.AuthorizeToolInvocationUseCase;
import io.github.tikyparkinson.mcpguardrails.authz.domain.PermissionEffect;
import io.github.tikyparkinson.mcpguardrails.authz.domain.PolicyDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthzGuardrailTest {

  private static final ToolInvocationContext CONTEXT =
      new ToolInvocationContext(
          new AgentId("agent-1"),
          new ToolName("search"),
          Instant.parse("2026-07-24T10:00:00Z"),
          Map.of(),
          Map.of());

  private final AuthorizeToolInvocationUseCase authorize =
      mock(AuthorizeToolInvocationUseCase.class);
  private final RecordAuditEventUseCase auditBus = mock(RecordAuditEventUseCase.class);
  private final AuthzGuardrail guardrail = new AuthzGuardrail(authorize, auditBus);

  @Test
  void shouldAllowAndRecordDecisionWhenPolicyAllows() {
    // given
    when(authorize.authorize("agent-1", "search"))
        .thenReturn(new PolicyDecision(PermissionEffect.ALLOW, "rule[1]"));

    // when / then
    assertEquals(new Allow(), guardrail.evaluate(CONTEXT));
    verify(auditBus)
        .record(
            new NewAuditEvent(
                "agent-1", "search", "authz", AuditEventType.DECISION_ALLOW, "rule[1]"));
  }

  @Test
  void shouldDenyWithReasonWhenPolicyDenies() {
    // given
    when(authorize.authorize("agent-1", "search"))
        .thenReturn(new PolicyDecision(PermissionEffect.DENY, "rule[0]"));

    // when / then
    assertEquals(
        new Deny("agent 'agent-1' is not allowed to call tool 'search' (rule[0])"),
        guardrail.evaluate(CONTEXT));
    verify(auditBus)
        .record(
            new NewAuditEvent(
                "agent-1", "search", "authz", AuditEventType.DECISION_DENY, "rule[0]"));
  }

  @Test
  void shouldEscalateWithReasonWhenPolicyEscalates() {
    // given
    when(authorize.authorize("agent-1", "search"))
        .thenReturn(new PolicyDecision(PermissionEffect.ESCALATE, "default"));

    // when / then
    assertEquals(
        new Escalate("agent 'agent-1' requires approval for tool 'search' (default)"),
        guardrail.evaluate(CONTEXT));
    verify(auditBus)
        .record(
            new NewAuditEvent(
                "agent-1", "search", "authz", AuditEventType.DECISION_ESCALATE, "default"));
  }

  @Test
  void shouldPropagateFailureWhenAuditBusThrows() {
    // given: fail-closed — an unauditable decision must not pass silently
    when(authorize.authorize("agent-1", "search"))
        .thenReturn(new PolicyDecision(PermissionEffect.ALLOW, "default"));
    when(auditBus.record(any())).thenThrow(new IllegalStateException("audit store down"));

    // when / then
    assertThrows(IllegalStateException.class, () -> guardrail.evaluate(CONTEXT));
  }

  @Test
  void shouldExposeStableNameAndDefaultOrderWhenQueried() {
    // given / when / then
    assertEquals("authz", guardrail.name());
    assertEquals(0, guardrail.order());
  }

  @Test
  void shouldRejectNullCollaboratorsWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new AuthzGuardrail(null, auditBus));
    assertThrows(NullPointerException.class, () -> new AuthzGuardrail(authorize, null));
  }
}
