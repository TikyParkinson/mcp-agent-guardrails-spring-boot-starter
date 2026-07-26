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
package io.github.tikyparkinson.mcpguardrails.trifecta.adapter.in.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.core.adapter.in.mcp.GuardedToolCallHandler;
import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.SessionId;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SessionIdResolverTest {

  private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");
  private static final SessionIdResolver RESOLVER = SessionIdResolver.mcpSessionOrAgent();

  @Test
  void shouldUseTheTransportSessionWhenTheInvocationCarriesOne() {
    // given an invocation carrying an MCP session
    ToolInvocationContext context = context("copilot", "6a78fc9c-f807-4bf7-94dd-0c94a1db6808");

    // when the session is resolved
    SessionId sessionId = RESOLVER.resolve(context);

    // then that is what correlation keys on
    assertEquals(SessionId.ofMcpSession("6a78fc9c-f807-4bf7-94dd-0c94a1db6808"), sessionId);
    assertFalse(SessionIdResolver.isFallback(sessionId));
  }

  @Test
  void shouldKeepTwoConnectionsOfTheSameClientApart() {
    // given two invocations from the same client product on different connections
    ToolInvocationContext first = context("copilot", "sess-A");
    ToolInvocationContext second = context("copilot", "sess-B");

    // when both are resolved
    // then they are different sessions. This is the whole point: the agent identifier is the
    // client product's name, so correlating on it would close the triangle across people who read
    // a record, opened a URL and sent an email without ever meeting
    assertNotEquals(RESOLVER.resolve(first), RESOLVER.resolve(second));
  }

  @Test
  void shouldFallBackToTheAgentWhenThereIsNoTransportSession() {
    // given an invocation with no session in its metadata
    ToolInvocationContext context =
        new ToolInvocationContext(
            new AgentId("copilot"), new ToolName("t"), NOW, Map.of(), Map.of());

    // when the session is resolved
    SessionId sessionId = RESOLVER.resolve(context);

    // then it degrades to the agent, and says so, because on this path unrelated work correlates
    // together
    assertEquals(SessionId.ofAgent("copilot"), sessionId);
    assertTrue(SessionIdResolver.isFallback(sessionId));
  }

  @Test
  void shouldFallBackWhenTheSessionInTheMetadataIsBlank() {
    // given a blank session value
    ToolInvocationContext context = context("copilot", "   ");

    // when the session is resolved
    // then a blank value counts as no session rather than as a session named " "
    assertTrue(SessionIdResolver.isFallback(RESOLVER.resolve(context)));
  }

  @Test
  void shouldFallBackWhenTheMetadataValueIsNotText() {
    // given metadata holding something that is not a session string
    ToolInvocationContext context =
        new ToolInvocationContext(
            new AgentId("copilot"),
            new ToolName("t"),
            NOW,
            Map.of(),
            Map.of(GuardedToolCallHandler.SESSION_ID, 42));

    // when the session is resolved
    // then it degrades rather than failing the invocation over a malformed optional field
    assertTrue(SessionIdResolver.isFallback(RESOLVER.resolve(context)));
  }

  @Test
  void shouldRejectResolvingWithoutAContext() {
    // given no context
    // when resolved
    // then it fails
    assertThrows(NullPointerException.class, () -> RESOLVER.resolve(null));
  }

  @Test
  void shouldRejectCheckingAFallbackWithoutASession() {
    // given no session
    // when checked
    // then it fails
    assertThrows(NullPointerException.class, () -> SessionIdResolver.isFallback(null));
  }

  private static ToolInvocationContext context(String agentId, String sessionId) {
    return new ToolInvocationContext(
        new AgentId(agentId),
        new ToolName("read_customer"),
        NOW,
        Map.of(),
        Map.of(GuardedToolCallHandler.SESSION_ID, sessionId));
  }
}
