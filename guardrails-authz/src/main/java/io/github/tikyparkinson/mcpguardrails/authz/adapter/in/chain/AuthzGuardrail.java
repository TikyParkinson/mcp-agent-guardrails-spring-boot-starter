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

import io.github.tikyparkinson.mcpguardrails.audit.application.port.in.RecordAuditEventUseCase;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEventType;
import io.github.tikyparkinson.mcpguardrails.audit.domain.NewAuditEvent;
import io.github.tikyparkinson.mcpguardrails.authz.application.port.in.AuthorizeToolInvocationUseCase;
import io.github.tikyparkinson.mcpguardrails.authz.domain.PolicyDecision;
import io.github.tikyparkinson.mcpguardrails.core.application.port.out.Guardrail;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import java.util.Objects;

/**
 * Authorization guardrail: evaluates the declarative agent-to-tool policy, records every decision
 * on the audit bus and translates the effect into a {@link GuardrailDecision}. Audit bus failures
 * propagate so the core chain fails closed.
 */
public final class AuthzGuardrail implements Guardrail {

  public static final String NAME = "authz";

  private final AuthorizeToolInvocationUseCase authorize;
  private final RecordAuditEventUseCase auditBus;

  public AuthzGuardrail(
      AuthorizeToolInvocationUseCase authorize, RecordAuditEventUseCase auditBus) {
    this.authorize = Objects.requireNonNull(authorize, "authorize");
    this.auditBus = Objects.requireNonNull(auditBus, "auditBus");
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public GuardrailDecision evaluate(ToolInvocationContext context) {
    String agentId = context.agentId().value();
    String toolName = context.toolName().value();
    PolicyDecision decision = authorize.authorize(agentId, toolName);
    recordDecision(agentId, toolName, decision);
    return toGuardrailDecision(agentId, toolName, decision);
  }

  private void recordDecision(String agentId, String toolName, PolicyDecision decision) {
    AuditEventType type =
        switch (decision.effect()) {
          case ALLOW -> AuditEventType.DECISION_ALLOW;
          case DENY -> AuditEventType.DECISION_DENY;
          case ESCALATE -> AuditEventType.DECISION_ESCALATE;
        };
    auditBus.record(new NewAuditEvent(agentId, toolName, NAME, type, decision.source()));
  }

  private static GuardrailDecision toGuardrailDecision(
      String agentId, String toolName, PolicyDecision decision) {
    return switch (decision.effect()) {
      case ALLOW -> new Allow();
      case DENY ->
          new Deny(
              "agent '%s' is not allowed to call tool '%s' (%s)"
                  .formatted(agentId, toolName, decision.source()));
      case ESCALATE ->
          new Escalate(
              "agent '%s' requires approval for tool '%s' (%s)"
                  .formatted(agentId, toolName, decision.source()));
    };
  }
}
