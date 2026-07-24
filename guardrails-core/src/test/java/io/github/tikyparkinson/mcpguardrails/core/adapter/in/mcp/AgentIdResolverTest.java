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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

class AgentIdResolverTest {

  @Test
  void shouldResolveClientNameWhenClientInfoPresent() {
    // given
    McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
    when(exchange.getClientInfo()).thenReturn(new McpSchema.Implementation("my-agent", "2.1"));

    // when / then
    assertEquals("my-agent", AgentIdResolver.clientInfoName().resolve(exchange).value());
  }

  @Test
  void shouldResolveUnknownWhenClientInfoMissing() {
    // given
    McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
    when(exchange.getClientInfo()).thenReturn(null);

    // when / then
    assertEquals("unknown", AgentIdResolver.clientInfoName().resolve(exchange).value());
  }

  @Test
  void shouldResolveUnknownWhenClientNameBlank() {
    // given
    McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
    when(exchange.getClientInfo()).thenReturn(new McpSchema.Implementation("  ", "2.1"));

    // when / then
    assertEquals("unknown", AgentIdResolver.clientInfoName().resolve(exchange).value());
  }

  @Test
  void shouldResolveUnknownWhenClientNameIsNull() {
    // given: the SDK forbids null names, but the resolver defends against contract violations
    McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
    McpSchema.Implementation clientInfo = mock(McpSchema.Implementation.class);
    when(clientInfo.name()).thenReturn(null);
    when(exchange.getClientInfo()).thenReturn(clientInfo);

    // when / then
    assertEquals("unknown", AgentIdResolver.clientInfoName().resolve(exchange).value());
  }

  @Test
  void shouldResolveUnknownWhenExchangeIsNull() {
    // given / when / then
    assertEquals("unknown", AgentIdResolver.clientInfoName().resolve(null).value());
  }
}
