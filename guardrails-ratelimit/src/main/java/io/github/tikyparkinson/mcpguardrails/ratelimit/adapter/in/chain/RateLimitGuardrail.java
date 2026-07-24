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
package io.github.tikyparkinson.mcpguardrails.ratelimit.adapter.in.chain;

import io.github.tikyparkinson.mcpguardrails.audit.application.port.in.RecordAuditEventUseCase;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEventType;
import io.github.tikyparkinson.mcpguardrails.audit.domain.NewAuditEvent;
import io.github.tikyparkinson.mcpguardrails.core.application.port.out.Guardrail;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.ratelimit.application.port.in.CheckRateLimitUseCase;
import io.github.tikyparkinson.mcpguardrails.ratelimit.domain.RateLimitStatus;
import java.util.Objects;

/**
 * Rate limit guardrail: registers each invocation in its fixed window and denies once the (agent,
 * tool) pair exceeds the configured limit. Allowed invocations pass silently (anti-noise); denials
 * are recorded on the audit bus. Store/bus failures propagate so the core chain fails closed.
 */
public final class RateLimitGuardrail implements Guardrail {

  public static final String NAME = "ratelimit";

  private final CheckRateLimitUseCase checkRateLimit;
  private final RecordAuditEventUseCase auditBus;

  public RateLimitGuardrail(
      CheckRateLimitUseCase checkRateLimit, RecordAuditEventUseCase auditBus) {
    this.checkRateLimit = Objects.requireNonNull(checkRateLimit, "checkRateLimit");
    this.auditBus = Objects.requireNonNull(auditBus, "auditBus");
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public int order() {
    return 100;
  }

  @Override
  public GuardrailDecision evaluate(ToolInvocationContext context) {
    String agentId = context.agentId().value();
    String toolName = context.toolName().value();
    RateLimitStatus status = checkRateLimit.check(agentId, toolName, context.occurredAt());
    if (status.allowed()) {
      return new Allow();
    }
    recordDenial(agentId, toolName, status);
    return new Deny(
        "rate limit exceeded for agent '%s' on tool '%s' (%d/%d in %s)"
            .formatted(
                agentId,
                toolName,
                status.count(),
                status.policy().maxInvocations(),
                status.policy().window()));
  }

  private void recordDenial(String agentId, String toolName, RateLimitStatus status) {
    String detail =
        "count=%d limit=%d window=%s"
            .formatted(status.count(), status.policy().maxInvocations(), status.policy().window());
    auditBus.record(
        new NewAuditEvent(agentId, toolName, NAME, AuditEventType.DECISION_DENY, detail));
  }
}
