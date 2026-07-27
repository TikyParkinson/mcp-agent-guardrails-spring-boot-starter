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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolInvocationUseCase;
import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.ChainVerdict;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The MCP transport session as it reaches a guardrail, through the invocation metadata. */
class GuardedToolCallHandlerSessionTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneOffset.UTC);
  private static final McpSchema.CallToolResult TOOL_RESULT =
      McpSchema.CallToolResult.builder().addTextContent("tool ran").build();

  @Test
  void shouldCarryTheTransportSessionInTheInvocationMetadata() {
    // given an exchange whose transport supplies a session
    McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
    when(exchange.sessionId()).thenReturn("6a78fc9c-f807-4bf7-94dd-0c94a1db6808");
    AtomicReference<ToolInvocationContext> seen = new AtomicReference<>();

    // when an invocation is evaluated
    handlerCapturing(seen).apply(exchange, request());

    // then a guardrail can tell one connection from another, which the agent identifier cannot do:
    // it is the client product's name and is shared by everybody using it
    assertEquals(
        Map.of(GuardedToolCallHandler.SESSION_ID, "6a78fc9c-f807-4bf7-94dd-0c94a1db6808"),
        seen.get().metadata());
  }

  @Test
  void shouldLeaveTheKeyOutWhenTheTransportHasNoSession() {
    // given a transport that supplies no session
    McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
    when(exchange.sessionId()).thenReturn(null);
    AtomicReference<ToolInvocationContext> seen = new AtomicReference<>();

    // when an invocation is evaluated
    handlerCapturing(seen).apply(exchange, request());

    // then the key is absent rather than present and empty, so a consumer that finds it can trust
    // there is a session without repeating the check
    assertFalse(seen.get().metadata().containsKey(GuardedToolCallHandler.SESSION_ID));
  }

  @Test
  void shouldLeaveTheKeyOutWhenTheSessionIsBlank() {
    // given a transport that supplies a blank session
    McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
    when(exchange.sessionId()).thenReturn("   ");
    AtomicReference<ToolInvocationContext> seen = new AtomicReference<>();

    // when an invocation is evaluated
    handlerCapturing(seen).apply(exchange, request());

    // then it counts as no session at all
    assertFalse(seen.get().metadata().containsKey(GuardedToolCallHandler.SESSION_ID));
  }

  @Test
  void shouldStillRunTheToolWhenTheTransportThrowsAskingForTheSession() {
    // given a transport whose session lookup fails
    McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
    when(exchange.sessionId()).thenThrow(new UnsupportedOperationException("no sessions here"));
    AtomicReference<ToolInvocationContext> seen = new AtomicReference<>();

    // when an invocation is evaluated
    McpSchema.CallToolResult result = handlerCapturing(seen).apply(exchange, request());

    // then the invocation goes through with no session: the transport is implemented outside this
    // project, and no call should fail over an optional piece of context
    assertSame(TOOL_RESULT, result);
    assertTrue(seen.get().metadata().isEmpty());
  }

  @Test
  void shouldUseNoMetadataWhenThereIsNoExchange() {
    // given no exchange at all
    AtomicReference<ToolInvocationContext> seen = new AtomicReference<>();

    // when an invocation is evaluated
    handlerCapturing(seen).apply(null, request());

    // then the metadata is empty rather than dereferencing nothing
    assertTrue(seen.get().metadata().isEmpty());
  }

  private static GuardedToolCallHandler handlerCapturing(
      AtomicReference<ToolInvocationContext> seen) {
    EvaluateToolInvocationUseCase capturing =
        context -> {
          seen.set(context);
          return new ChainVerdict(new Allow(), List.of());
        };
    return new GuardedToolCallHandler(
        (ex, req) -> TOOL_RESULT, capturing, ex -> new AgentId("copilot"), FIXED_CLOCK);
  }

  private static McpSchema.CallToolRequest request() {
    return new McpSchema.CallToolRequest("search", Map.of("q", "x"));
  }
}
