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
 * Authorization guardrail: evaluates the declarative agent-to-tool policy and translates the effect
 * into a {@link GuardrailDecision}.
 *
 * <p>It does not publish to the audit bus. ARCHITECTURE.md §5 forbids one guardrail module from
 * depending on another, and auditing happens once for the whole chain in {@code
 * spring-boot-starter}. The matching rule travels in the decision itself — including on an {@code
 * Allow}, which is why {@code Allow} carries a reason at all.
 */
public final class AuthzGuardrail implements Guardrail {

  public static final String GUARDRAIL_NAME = "authz";

  private final AuthorizeToolInvocationUseCase authorize;

  public AuthzGuardrail(AuthorizeToolInvocationUseCase authorize) {
    this.authorize = Objects.requireNonNull(authorize, "authorize");
  }

  @Override
  public String name() {
    return GUARDRAIL_NAME;
  }

  @Override
  public GuardrailDecision evaluate(ToolInvocationContext context) {
    String agentId = context.agentId().value();
    String toolName = context.toolName().value();
    PolicyDecision decision = authorize.authorize(agentId, toolName);
    return toGuardrailDecision(agentId, toolName, decision);
  }

  private static GuardrailDecision toGuardrailDecision(
      String agentId, String toolName, PolicyDecision decision) {
    return switch (decision.effect()) {
      case ALLOW -> new Allow(decision.source());
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
