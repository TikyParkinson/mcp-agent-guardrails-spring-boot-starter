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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolInvocationUseCase;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.ChainVerdict;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class GuardedToolCallHandlerTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-07-24T10:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
  private static final McpSchema.CallToolResult DELEGATE_RESULT =
      McpSchema.CallToolResult.builder().addTextContent("tool ran").build();

  private final McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);

  @Test
  void shouldDelegateToOriginalHandlerWhenChainAllows() {
    // given
    GuardedToolCallHandler handler = handlerReturning(new Allow(), (ex, req) -> DELEGATE_RESULT);
    McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("search", Map.of("q", "x"));

    // when
    McpSchema.CallToolResult result = handler.apply(exchange, request);

    // then
    assertSame(DELEGATE_RESULT, result);
  }

  @Test
  void shouldReturnErrorWithoutRunningToolWhenChainDenies() {
    // given
    @SuppressWarnings("unchecked")
    BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>
        delegate = mock(BiFunction.class);
    GuardedToolCallHandler handler = handlerReturning(new Deny("agent not allowed"), delegate);
    McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("search", Map.of());

    // when
    McpSchema.CallToolResult result = handler.apply(exchange, request);

    // then
    assertTrue(result.isError());
    assertEquals("Tool call denied by guardrails: agent not allowed", firstText(result));
    verifyNoInteractions(delegate);
  }

  @Test
  void shouldReturnErrorWithoutRunningToolWhenChainEscalates() {
    // given
    @SuppressWarnings("unchecked")
    BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>
        delegate = mock(BiFunction.class);
    GuardedToolCallHandler handler = handlerReturning(new Escalate("sensitive tool"), delegate);
    McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("delete_all", Map.of());

    // when
    McpSchema.CallToolResult result = handler.apply(exchange, request);

    // then
    assertTrue(result.isError());
    assertEquals(
        "Tool call requires approval (escalated by guardrails): sensitive tool", firstText(result));
    verifyNoInteractions(delegate);
  }

  @Test
  void shouldBuildContextFromRequestWhenInvoked() {
    // given
    AtomicReference<ToolInvocationContext> seen = new AtomicReference<>();
    EvaluateToolInvocationUseCase capturing =
        context -> {
          seen.set(context);
          return new ChainVerdict(new Allow(), List.of());
        };
    when(exchange.getClientInfo()).thenReturn(new McpSchema.Implementation("copilot", "1.0"));
    GuardedToolCallHandler handler =
        new GuardedToolCallHandler(
            (ex, req) -> DELEGATE_RESULT, capturing, AgentIdResolver.clientInfoName(), FIXED_CLOCK);

    // when
    handler.apply(exchange, new McpSchema.CallToolRequest("search", Map.of("q", "42")));

    // then
    ToolInvocationContext context = seen.get();
    assertEquals("copilot", context.agentId().value());
    assertEquals("search", context.toolName().value());
    assertEquals(FIXED_NOW, context.occurredAt());
    assertEquals(Map.of("q", "42"), context.arguments());
  }

  @Test
  void shouldUseEmptyArgumentsWhenRequestArgumentsAreNull() {
    // given
    AtomicReference<ToolInvocationContext> seen = new AtomicReference<>();
    EvaluateToolInvocationUseCase capturing =
        context -> {
          seen.set(context);
          return new ChainVerdict(new Allow(), List.of());
        };
    GuardedToolCallHandler handler =
        new GuardedToolCallHandler(
            (ex, req) -> DELEGATE_RESULT, capturing, ex -> agent("a"), FIXED_CLOCK);

    // when
    handler.apply(exchange, new McpSchema.CallToolRequest("search", (Map<String, Object>) null));

    // then
    assertEquals(Map.of(), seen.get().arguments());
  }

  private GuardedToolCallHandler handlerReturning(
      GuardrailDecision decision,
      BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>
          delegate) {
    EvaluateToolInvocationUseCase useCase = context -> new ChainVerdict(decision, List.of());
    return new GuardedToolCallHandler(delegate, useCase, ex -> agent("agent-1"), FIXED_CLOCK);
  }

  private static io.github.tikyparkinson.mcpguardrails.core.domain.AgentId agent(String value) {
    return new io.github.tikyparkinson.mcpguardrails.core.domain.AgentId(value);
  }

  private static String firstText(McpSchema.CallToolResult result) {
    return ((McpSchema.TextContent) result.content().get(0)).text();
  }
}
