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
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Inbound MCP adapter: decorates a tool call handler so that every invocation is evaluated by the
 * guardrail chain before the real tool runs.
 *
 * <p>{@code Allow} delegates to the original handler; {@code Deny} and {@code Escalate} return an
 * error {@link McpSchema.CallToolResult} without executing the tool ({@code Escalate} is handled
 * conservatively until a human-in-the-loop mechanism exists).
 */
public final class GuardedToolCallHandler
    implements BiFunction<
        McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> {

  private final BiFunction<
          McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>
      delegate;
  private final EvaluateToolInvocationUseCase useCase;
  private final AgentIdResolver agentIdResolver;
  private final Clock clock;

  public GuardedToolCallHandler(
      BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>
          delegate,
      EvaluateToolInvocationUseCase useCase,
      AgentIdResolver agentIdResolver,
      Clock clock) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.useCase = Objects.requireNonNull(useCase, "useCase");
    this.agentIdResolver = Objects.requireNonNull(agentIdResolver, "agentIdResolver");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public McpSchema.CallToolResult apply(
      McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
    ToolInvocationContext context = toContext(exchange, request);
    return switch (useCase.evaluate(context).finalDecision()) {
      case Allow _ -> delegate.apply(exchange, request);
      case Deny(String reason) -> errorResult("Tool call denied by guardrails: " + reason);
      case Escalate(String reason) ->
          errorResult("Tool call requires approval (escalated by guardrails): " + reason);
    };
  }

  private ToolInvocationContext toContext(
      McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
    Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
    return new ToolInvocationContext(
        agentIdResolver.resolve(exchange),
        new ToolName(request.name()),
        clock.instant(),
        arguments,
        Map.of());
  }

  private static McpSchema.CallToolResult errorResult(String message) {
    return McpSchema.CallToolResult.builder().isError(true).addTextContent(message).build();
  }
}
