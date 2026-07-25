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
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Block;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.PassThrough;
import io.github.tikyparkinson.mcpguardrails.core.domain.Redact;
import io.github.tikyparkinson.mcpguardrails.core.domain.ResultVerdict;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolResultContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Inbound MCP adapter: decorates a tool call handler so that every invocation is evaluated by the
 * guardrail chain before the real tool runs, and its result by the outbound chain before it reaches
 * the agent.
 *
 * <p>{@code Allow} delegates to the original handler; {@code Deny} and {@code Escalate} return an
 * error {@link McpSchema.CallToolResult} without executing the tool ({@code Escalate} is handled
 * conservatively until a human-in-the-loop mechanism exists). On the way back, {@code PassThrough}
 * returns the result untouched, {@code Redact} rebuilds it with sanitized text, and {@code Block}
 * replaces it with an error.
 */
public final class GuardedToolCallHandler
    implements BiFunction<
        McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> {

  private static final EvaluateToolResultUseCase NO_OUTBOUND_GUARDRAILS =
      context -> new ResultVerdict(new PassThrough(), List.of());

  private final BiFunction<
          McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>
      delegate;
  private final EvaluateToolInvocationUseCase useCase;
  private final EvaluateToolResultUseCase resultUseCase;
  private final AgentIdResolver agentIdResolver;
  private final Clock clock;

  public GuardedToolCallHandler(
      BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>
          delegate,
      EvaluateToolInvocationUseCase useCase,
      AgentIdResolver agentIdResolver,
      Clock clock) {
    this(delegate, useCase, NO_OUTBOUND_GUARDRAILS, agentIdResolver, clock);
  }

  public GuardedToolCallHandler(
      BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>
          delegate,
      EvaluateToolInvocationUseCase useCase,
      EvaluateToolResultUseCase resultUseCase,
      AgentIdResolver agentIdResolver,
      Clock clock) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.useCase = Objects.requireNonNull(useCase, "useCase");
    this.resultUseCase = Objects.requireNonNull(resultUseCase, "resultUseCase");
    this.agentIdResolver = Objects.requireNonNull(agentIdResolver, "agentIdResolver");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public McpSchema.CallToolResult apply(
      McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
    ToolInvocationContext context = toContext(exchange, request);
    return switch (useCase.evaluate(context).finalDecision()) {
      case Allow _ -> guardedDelegate(exchange, request, context);
      case Deny(String reason) -> errorResult("Tool call denied by guardrails: " + reason);
      case Escalate(String reason) ->
          errorResult("Tool call requires approval (escalated by guardrails): " + reason);
    };
  }

  private McpSchema.CallToolResult guardedDelegate(
      McpSyncServerExchange exchange,
      McpSchema.CallToolRequest request,
      ToolInvocationContext invocation) {
    McpSchema.CallToolResult result = delegate.apply(exchange, request);
    ToolResultContext resultContext = toResultContext(result, invocation);
    return switch (resultUseCase.evaluate(resultContext).finalDecision()) {
      case PassThrough _ -> result;
      case Redact(List<String> sanitized, _) -> redacted(result, sanitized);
      case Block(String reason) -> errorResult("Tool result blocked by guardrails: " + reason);
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

  private static ToolResultContext toResultContext(
      McpSchema.CallToolResult result, ToolInvocationContext invocation) {
    return new ToolResultContext(
        invocation.agentId(),
        invocation.toolName(),
        invocation.occurredAt(),
        textContents(result),
        structuredContent(result),
        Boolean.TRUE.equals(result.isError()));
  }

  /**
   * Every redactable text of the result, in order: the text of each {@code TextContent} and of each
   * {@code EmbeddedResource} carrying textual contents. A tool returning a file as an embedded
   * resource is a first-class leak channel, so it must be inspectable too.
   */
  private static List<String> textContents(McpSchema.CallToolResult result) {
    return result.content().stream()
        .map(GuardedToolCallHandler::redactableText)
        .flatMap(Optional::stream)
        .toList();
  }

  private static Optional<String> redactableText(McpSchema.Content content) {
    return switch (content) {
      case McpSchema.TextContent text -> Optional.of(text.text());
      case McpSchema.EmbeddedResource embedded ->
          embedded.resource() instanceof McpSchema.TextResourceContents textual
              ? Optional.of(textual.text())
              : Optional.empty();
      default -> Optional.empty();
    };
  }

  /** Structured content is exposed read-only so guardrails can scan it; it is never rewritten. */
  private static Map<String, Object> structuredContent(McpSchema.CallToolResult result) {
    if (!(result.structuredContent() instanceof Map<?, ?> structured)) {
      return Map.of();
    }
    Map<String, Object> scannable = new LinkedHashMap<>();
    structured.forEach(
        (key, value) -> {
          if (value != null) {
            scannable.put(String.valueOf(key), value);
          }
        });
    return scannable;
  }

  private static McpSchema.CallToolResult redacted(
      McpSchema.CallToolResult result, List<String> sanitized) {
    List<McpSchema.Content> originals = result.content();
    List<McpSchema.Content> contents = new ArrayList<>(originals.size());
    int index = 0;
    for (McpSchema.Content content : originals) {
      if (redactableText(content).isPresent()) {
        contents.add(withText(content, sanitized.get(index++)));
      } else {
        contents.add(content);
      }
    }
    return new McpSchema.CallToolResult(
        contents, result.isError(), result.structuredContent(), result.meta());
  }

  /** Rebuilds a redactable content with new text, preserving everything else it carries. */
  private static McpSchema.Content withText(McpSchema.Content content, String text) {
    if (content instanceof McpSchema.TextContent original) {
      return new McpSchema.TextContent(original.annotations(), text, original.meta());
    }
    McpSchema.EmbeddedResource embedded = (McpSchema.EmbeddedResource) content;
    McpSchema.TextResourceContents original = (McpSchema.TextResourceContents) embedded.resource();
    return new McpSchema.EmbeddedResource(
        embedded.annotations(),
        new McpSchema.TextResourceContents(
            original.uri(), original.mimeType(), text, original.meta()),
        embedded.meta());
  }

  private static McpSchema.CallToolResult errorResult(String message) {
    return McpSchema.CallToolResult.builder().isError(true).addTextContent(message).build();
  }
}
