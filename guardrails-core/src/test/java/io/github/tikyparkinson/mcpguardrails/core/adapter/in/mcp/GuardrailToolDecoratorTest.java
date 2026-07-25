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
import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolResultUseCase;
import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Block;
import io.github.tikyparkinson.mcpguardrails.core.domain.ChainVerdict;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.PassThrough;
import io.github.tikyparkinson.mcpguardrails.core.domain.ResultVerdict;
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

  @Test
  void shouldGuardBothDirectionsWhenDecoratedWithOutboundChain() {
    // given
    McpSchema.Tool tool = McpSchema.Tool.builder("read_file").build();
    McpServerFeatures.SyncToolSpecification original =
        McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(
                (ex, req) ->
                    McpSchema.CallToolResult.builder().addTextContent("key=sk-live-1").build())
            .build();
    EvaluateToolInvocationUseCase allowAll = context -> new ChainVerdict(new Allow(), List.of());
    EvaluateToolResultUseCase blockLeaks =
        context -> new ResultVerdict(new Block("credential detected"), List.of());

    // when
    McpServerFeatures.SyncToolSpecification decorated =
        GuardrailToolDecorator.decorate(
            original, allowAll, blockLeaks, ex -> new AgentId("a"), CLOCK);
    McpSchema.CallToolResult result =
        decorated
            .callHandler()
            .apply(
                mock(McpSyncServerExchange.class),
                new McpSchema.CallToolRequest("read_file", Map.of()));

    // then
    assertSame(tool, decorated.tool());
    assertTrue(result.isError());
    assertEquals(
        "Tool result blocked by guardrails: credential detected",
        ((McpSchema.TextContent) result.content().get(0)).text());
  }

  @Test
  void shouldRejectNullSpecificationWhenDecoratingWithOutboundChain() {
    // given
    EvaluateToolInvocationUseCase useCase = context -> new ChainVerdict(new Deny("x"), List.of());
    EvaluateToolResultUseCase outbound = context -> new ResultVerdict(new PassThrough(), List.of());

    // when / then
    assertThrows(
        NullPointerException.class,
        () ->
            GuardrailToolDecorator.decorate(
                null, useCase, outbound, ex -> new AgentId("a"), CLOCK));
  }
}
