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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolInvocationUseCase;
import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.ChainVerdict;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GuardrailToolDecoratorTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC);

  @Test
  void shouldKeepToolMetadataAndGuardHandlerWhenDecorated() {
    // given
    McpSchema.Tool tool = McpSchema.Tool.builder("dangerous_tool").build();
    McpServerFeatures.SyncToolSpecification original =
        McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(
                (ex, req) -> McpSchema.CallToolResult.builder().addTextContent("ran").build())
            .build();
    EvaluateToolInvocationUseCase denyAll =
        context -> new ChainVerdict(new Deny("blocked"), List.of());

    // when
    McpServerFeatures.SyncToolSpecification decorated =
        GuardrailToolDecorator.decorate(original, denyAll, ex -> new AgentId("a"), CLOCK);
    McpSchema.CallToolResult result =
        decorated
            .callHandler()
            .apply(
                mock(McpSyncServerExchange.class),
                new McpSchema.CallToolRequest("dangerous_tool", Map.of()));

    // then
    assertSame(tool, decorated.tool());
    assertTrue(result.isError());
    assertEquals(
        "Tool call denied by guardrails: blocked",
        ((McpSchema.TextContent) result.content().get(0)).text());
  }

  @Test
  void shouldRejectNullSpecificationWhenDecorating() {
    // given
    EvaluateToolInvocationUseCase useCase = context -> new ChainVerdict(new Deny("x"), List.of());

    // when / then
    assertThrows(
        NullPointerException.class,
        () -> GuardrailToolDecorator.decorate(null, useCase, ex -> new AgentId("a"), CLOCK));
  }
}
