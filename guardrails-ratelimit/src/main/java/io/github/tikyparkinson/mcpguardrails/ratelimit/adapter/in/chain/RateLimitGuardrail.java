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
 * tool) pair exceeds the configured limit. Store failures propagate so the core chain fails closed.
 *
 * <p>It does not publish to the audit bus — ARCHITECTURE.md §5 forbids depending on another
 * guardrail module, and auditing happens once for the whole chain in {@code spring-boot-starter}.
 * The counts that used to travel in the audit detail are in the denial reason instead.
 */
public final class RateLimitGuardrail implements Guardrail {

  public static final String GUARDRAIL_NAME = "ratelimit";

  private final CheckRateLimitUseCase checkRateLimit;

  public RateLimitGuardrail(CheckRateLimitUseCase checkRateLimit) {
    this.checkRateLimit = Objects.requireNonNull(checkRateLimit, "checkRateLimit");
  }

  @Override
  public String name() {
    return GUARDRAIL_NAME;
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
    return new Deny(
        "rate limit exceeded for agent '%s' on tool '%s' (%d/%d in %s)"
            .formatted(
                agentId,
                toolName,
                status.count(),
                status.policy().maxInvocations(),
                status.policy().window()));
  }
}
