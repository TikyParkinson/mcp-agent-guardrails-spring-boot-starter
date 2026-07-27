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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
  private final AuthzGuardrail guardrail = new AuthzGuardrail(authorize);

  @Test
  void shouldCarryTheMatchingRuleWhenPolicyAllows() {
    // given a policy that permits because of a specific rule
    when(authorize.authorize("agent-1", "search"))
        .thenReturn(new PolicyDecision(PermissionEffect.ALLOW, "rule[1]"));

    // when the guardrail evaluates
    // then the rule travels in the decision. Without it an audited Allow would say that the call
    // was permitted but not by which rule, which is close to useless to whoever reviews the trail
    assertEquals(new Allow("rule[1]"), guardrail.evaluate(CONTEXT));
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
  }

  @Test
  void shouldNotDependOnTheAuditBusWhenEvaluating() {
    // given a guardrail built with its only collaborator, the policy
    when(authorize.authorize("agent-1", "search"))
        .thenReturn(new PolicyDecision(PermissionEffect.ALLOW, "default"));

    // when it evaluates
    // then it decides on its own. ARCHITECTURE.md 5 forbids depending on another guardrail
    // module, so this guardrail no longer publishes anything and cannot fail because of a broken
    // audit store — the wiring layer records the whole chain instead
    assertEquals(new Allow("default"), guardrail.evaluate(CONTEXT));
  }

  @Test
  void shouldExposeStableNameAndDefaultOrderWhenQueried() {
    // given / when / then
    assertEquals("authz", guardrail.name());
    assertEquals(0, guardrail.order());
  }

  @Test
  void shouldRejectNullCollaboratorWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new AuthzGuardrail(null));
  }
}
