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
package io.github.tikyparkinson.mcpguardrails.core.adapter.in.mcp;

import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolInvocationUseCase;
import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolResultUseCase;
import io.github.tikyparkinson.mcpguardrails.core.application.port.out.EscalationResolver;
import io.modelcontextprotocol.server.McpServerFeatures;
import java.time.Clock;
import java.util.Objects;

/** Wraps MCP tool specifications so their handlers run behind the guardrail chain. */
public final class GuardrailToolDecorator {

  private static final String SPECIFICATION = "specification";

  private GuardrailToolDecorator() {}

  /** Returns a copy of the given tool specification with its call handler guarded. */
  public static McpServerFeatures.SyncToolSpecification decorate(
      McpServerFeatures.SyncToolSpecification specification,
      EvaluateToolInvocationUseCase useCase,
      AgentIdResolver agentIdResolver,
      Clock clock) {
    Objects.requireNonNull(specification, SPECIFICATION);
    return new McpServerFeatures.SyncToolSpecification(
        specification.tool(),
        new GuardedToolCallHandler(specification.callHandler(), useCase, agentIdResolver, clock));
  }

  /**
   * Returns a copy of the given tool specification with its call handler guarded on both
   * directions: the invocation by the guardrail chain and the result by the outbound chain.
   */
  public static McpServerFeatures.SyncToolSpecification decorate(
      McpServerFeatures.SyncToolSpecification specification,
      EvaluateToolInvocationUseCase useCase,
      EvaluateToolResultUseCase resultUseCase,
      AgentIdResolver agentIdResolver,
      Clock clock) {
    Objects.requireNonNull(specification, SPECIFICATION);
    return new McpServerFeatures.SyncToolSpecification(
        specification.tool(),
        new GuardedToolCallHandler(
            specification.callHandler(), useCase, resultUseCase, agentIdResolver, clock));
  }

  /**
   * Returns a copy of the given tool specification with its call handler guarded on both directions
   * and with an escalation resolver, so an {@code Escalate} verdict can become something other than
   * an error returned to the agent.
   *
   * @param escalationResolver decides what an escalated invocation actually does; {@code null}
   *     keeps the historical behaviour, exactly as the handler defines it
   */
  public static McpServerFeatures.SyncToolSpecification decorate(
      McpServerFeatures.SyncToolSpecification specification,
      EvaluateToolInvocationUseCase useCase,
      EvaluateToolResultUseCase resultUseCase,
      AgentIdResolver agentIdResolver,
      Clock clock,
      EscalationResolver escalationResolver) {
    Objects.requireNonNull(specification, SPECIFICATION);
    return new McpServerFeatures.SyncToolSpecification(
        specification.tool(),
        new GuardedToolCallHandler(
            specification.callHandler(),
            useCase,
            resultUseCase,
            agentIdResolver,
            clock,
            escalationResolver));
  }
}
