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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolInvocationUseCase;
import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolResultUseCase;
import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Block;
import io.github.tikyparkinson.mcpguardrails.core.domain.ChainVerdict;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.PassThrough;
import io.github.tikyparkinson.mcpguardrails.core.domain.Redact;
import io.github.tikyparkinson.mcpguardrails.core.domain.ResultDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ResultVerdict;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolResultContext;
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

/** Covers the outbound half of the handler: what happens to the result after the tool ran. */
class GuardedToolCallHandlerOutboundTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-07-26T10:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
  private static final McpSchema.CallToolRequest REQUEST =
      new McpSchema.CallToolRequest("read_file", Map.of("path", "/etc/env"));

  private final McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);

  private GuardedToolCallHandler handler(
      BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>
          delegate,
      EvaluateToolResultUseCase outbound) {
    EvaluateToolInvocationUseCase allow = context -> new ChainVerdict(new Allow(), List.of());
    return new GuardedToolCallHandler(
        delegate, allow, outbound, ex -> new AgentId("copilot"), FIXED_CLOCK);
  }

  private static EvaluateToolResultUseCase deciding(ResultDecision decision) {
    return context -> new ResultVerdict(decision, List.of());
  }

  private static String textAt(McpSchema.CallToolResult result, int index) {
    return ((McpSchema.TextContent) result.content().get(index)).text();
  }

  @Test
  void shouldReturnTheSameResultInstanceWhenOutboundChainPassesThrough() {
    // given: the majority case must not rebuild the result
    McpSchema.CallToolResult original =
        McpSchema.CallToolResult.builder().addTextContent("harmless").build();
    GuardedToolCallHandler handler = handler((ex, req) -> original, deciding(new PassThrough()));

    // when
    McpSchema.CallToolResult result = handler.apply(exchange, REQUEST);

    // then
    assertSame(original, result);
  }

  @Test
  void shouldReplaceTextContentsWhenOutboundChainRedacts() {
    // given
    McpSchema.CallToolResult original =
        McpSchema.CallToolResult.builder()
            .addTextContent("key=sk-live-1")
            .addTextContent("nothing here")
            .build();
    GuardedToolCallHandler handler =
        handler(
            (ex, req) -> original,
            deciding(new Redact(List.of("key=sk-****", "nothing here"), "api key")));

    // when
    McpSchema.CallToolResult result = handler.apply(exchange, REQUEST);

    // then
    assertEquals("key=sk-****", textAt(result, 0));
    assertEquals("nothing here", textAt(result, 1));
  }

  @Test
  void shouldPreserveErrorFlagAndStructuredContentWhenRedacting() {
    // given: redaction must not silently drop the rest of the result
    McpSchema.CallToolResult original =
        new McpSchema.CallToolResult(
            List.of(new McpSchema.TextContent("key=sk-live-1")),
            Boolean.TRUE,
            Map.of("status", "failed"),
            Map.of("trace", "abc"));
    GuardedToolCallHandler handler =
        handler((ex, req) -> original, deciding(new Redact(List.of("key=****"), "api key")));

    // when
    McpSchema.CallToolResult result = handler.apply(exchange, REQUEST);

    // then
    assertTrue(result.isError());
    assertEquals(Map.of("status", "failed"), result.structuredContent());
    assertEquals(Map.of("trace", "abc"), result.meta());
  }

  @Test
  void shouldKeepNonTextualContentsUntouchedWhenRedacting() {
    // given: only text is redactable; anything else is passed through verbatim
    McpSchema.Content image = new McpSchema.ImageContent(null, "base64data", "image/png");
    McpSchema.CallToolResult original =
        new McpSchema.CallToolResult(
            List.of(image, new McpSchema.TextContent("key=sk-live-1")), false, null, null);
    GuardedToolCallHandler handler =
        handler((ex, req) -> original, deciding(new Redact(List.of("key=****"), "api key")));

    // when
    McpSchema.CallToolResult result = handler.apply(exchange, REQUEST);

    // then
    assertSame(image, result.content().get(0));
    assertEquals("key=****", textAt(result, 1));
  }

  @Test
  void shouldReturnErrorWhenOutboundChainBlocks() {
    // given
    McpSchema.CallToolResult original =
        McpSchema.CallToolResult.builder().addTextContent("password=hunter2").build();
    GuardedToolCallHandler handler =
        handler((ex, req) -> original, deciding(new Block("credential detected")));

    // when
    McpSchema.CallToolResult result = handler.apply(exchange, REQUEST);

    // then
    assertTrue(result.isError());
    assertEquals("Tool result blocked by guardrails: credential detected", textAt(result, 0));
  }

  @Test
  void shouldNotInspectResultWhenInboundChainDenies() {
    // given: the tool never ran, so there is no result to inspect
    EvaluateToolResultUseCase outbound = mock(EvaluateToolResultUseCase.class);
    @SuppressWarnings("unchecked")
    BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>
        delegate = mock(BiFunction.class);
    GuardedToolCallHandler handler =
        new GuardedToolCallHandler(
            delegate,
            context -> new ChainVerdict(new Deny("not allowed"), List.of()),
            outbound,
            ex -> new AgentId("copilot"),
            FIXED_CLOCK);

    // when
    handler.apply(exchange, REQUEST);

    // then
    verifyNoInteractions(outbound, delegate);
  }

  @Test
  void shouldBuildResultContextFromResultAndInvocationWhenInspecting() {
    // given
    AtomicReference<ToolResultContext> seen = new AtomicReference<>();
    McpSchema.CallToolResult original =
        new McpSchema.CallToolResult(
            List.of(new McpSchema.TextContent("body")),
            Boolean.TRUE,
            Map.of("token", "sk-live-1"),
            null);
    GuardedToolCallHandler handler =
        handler(
            (ex, req) -> original,
            context -> {
              seen.set(context);
              return new ResultVerdict(new PassThrough(), List.of());
            });

    // when
    handler.apply(exchange, REQUEST);

    // then
    ToolResultContext context = seen.get();
    assertEquals("copilot", context.agentId().value());
    assertEquals("read_file", context.toolName().value());
    assertEquals(FIXED_NOW, context.occurredAt());
    assertEquals(List.of("body"), context.textContents());
    assertEquals(Map.of("token", "sk-live-1"), context.structuredContent());
    assertTrue(context.error());
  }

  @Test
  void shouldExposeEmptyStructuredContentWhenResultHasNone() {
    // given: structuredContent is optional and may be of any type
    AtomicReference<ToolResultContext> seen = new AtomicReference<>();
    McpSchema.CallToolResult original =
        new McpSchema.CallToolResult(List.of(), null, "not a map", null);
    GuardedToolCallHandler handler =
        handler(
            (ex, req) -> original,
            context -> {
              seen.set(context);
              return new ResultVerdict(new PassThrough(), List.of());
            });

    // when
    handler.apply(exchange, REQUEST);

    // then
    assertEquals(Map.of(), seen.get().structuredContent());
    assertFalse(seen.get().error());
  }

  @Test
  void shouldSkipNullStructuredValuesWhenBuildingContext() {
    // given: null values carry no secret and would break the immutable copy
    AtomicReference<ToolResultContext> seen = new AtomicReference<>();
    Map<String, Object> structured = new java.util.HashMap<>();
    structured.put("present", "value");
    structured.put("absent", null);
    McpSchema.CallToolResult original =
        new McpSchema.CallToolResult(List.of(), false, structured, null);
    GuardedToolCallHandler handler =
        handler(
            (ex, req) -> original,
            context -> {
              seen.set(context);
              return new ResultVerdict(new PassThrough(), List.of());
            });

    // when
    handler.apply(exchange, REQUEST);

    // then
    assertEquals(Map.of("present", "value"), seen.get().structuredContent());
  }

  @Test
  void shouldExposeEmbeddedResourceTextWhenBuildingContext() {
    // given: a tool returning a file as an embedded resource is a leak channel too
    AtomicReference<ToolResultContext> seen = new AtomicReference<>();
    McpSchema.CallToolResult original =
        new McpSchema.CallToolResult(
            List.of(
                new McpSchema.TextContent("plain"),
                new McpSchema.EmbeddedResource(
                    null,
                    new McpSchema.TextResourceContents(
                        "file:///app/.env", "text/plain", "API_KEY=sk-live-1"))),
            false,
            null,
            null);
    GuardedToolCallHandler handler =
        handler(
            (ex, req) -> original,
            context -> {
              seen.set(context);
              return new ResultVerdict(new PassThrough(), List.of());
            });

    // when
    handler.apply(exchange, REQUEST);

    // then
    assertEquals(List.of("plain", "API_KEY=sk-live-1"), seen.get().textContents());
  }

  @Test
  void shouldRedactEmbeddedResourceTextKeepingItsMetadata() {
    // given
    McpSchema.CallToolResult original =
        new McpSchema.CallToolResult(
            List.of(
                new McpSchema.EmbeddedResource(
                    null,
                    new McpSchema.TextResourceContents(
                        "file:///app/.env", "text/plain", "API_KEY=sk-live-1"))),
            false,
            null,
            null);
    GuardedToolCallHandler handler =
        handler(
            (ex, req) -> original,
            deciding(new Redact(List.of("API_KEY=[REDACTED:openai-api-key]"), "api key")));

    // when
    McpSchema.CallToolResult result = handler.apply(exchange, REQUEST);

    // then
    McpSchema.TextResourceContents redacted =
        (McpSchema.TextResourceContents)
            ((McpSchema.EmbeddedResource) result.content().get(0)).resource();
    assertEquals("API_KEY=[REDACTED:openai-api-key]", redacted.text());
    assertEquals("file:///app/.env", redacted.uri());
    assertEquals("text/plain", redacted.mimeType());
  }

  @Test
  void shouldIgnoreEmbeddedResourceWhenItsContentsAreNotTextual() {
    // given: a blob resource carries no scannable text and must pass through untouched
    AtomicReference<ToolResultContext> seen = new AtomicReference<>();
    McpSchema.Content blob =
        new McpSchema.EmbeddedResource(
            null,
            new McpSchema.BlobResourceContents("file:///app/logo.png", "image/png", "base64data"));
    McpSchema.CallToolResult original =
        new McpSchema.CallToolResult(
            List.of(blob, new McpSchema.TextContent("key=sk-live-1")), false, null, null);
    GuardedToolCallHandler handler =
        handler(
            (ex, req) -> original,
            context -> {
              seen.set(context);
              return new ResultVerdict(new PassThrough(), List.of());
            });

    // when
    handler.apply(exchange, REQUEST);

    // then
    assertEquals(List.of("key=sk-live-1"), seen.get().textContents());
  }

  @Test
  void shouldKeepNonRedactableContentsByIdentityWhenRedacting() {
    // given: positional replacement must skip whatever is not redactable
    McpSchema.Content blob =
        new McpSchema.EmbeddedResource(
            null,
            new McpSchema.BlobResourceContents("file:///app/logo.png", "image/png", "base64data"));
    McpSchema.CallToolResult original =
        new McpSchema.CallToolResult(
            List.of(blob, new McpSchema.TextContent("key=sk-live-1")), false, null, null);
    GuardedToolCallHandler handler =
        handler((ex, req) -> original, deciding(new Redact(List.of("key=****"), "api key")));

    // when
    McpSchema.CallToolResult result = handler.apply(exchange, REQUEST);

    // then
    assertSame(blob, result.content().get(0));
    assertEquals("key=****", textAt(result, 1));
  }

  @Test
  void shouldInspectResultsWithNoOutboundGuardrailsWhenUsingLegacyConstructor() {
    // given: the pre-existing 4-arg constructor must keep working and stay neutral
    McpSchema.CallToolResult original =
        McpSchema.CallToolResult.builder().addTextContent("untouched").build();
    GuardedToolCallHandler handler =
        new GuardedToolCallHandler(
            (ex, req) -> original,
            context -> new ChainVerdict(new Allow(), List.of()),
            ex -> new AgentId("copilot"),
            FIXED_CLOCK);

    // when
    McpSchema.CallToolResult result = handler.apply(exchange, REQUEST);

    // then
    assertSame(original, result);
  }

  @Test
  void shouldRejectNullOutboundUseCaseWhenConstructed() {
    // given
    EvaluateToolInvocationUseCase allow = context -> new ChainVerdict(new Allow(), List.of());
    AgentIdResolver resolver = ex -> new AgentId("copilot");

    // when / then
    assertNotNull(
        org.junit.jupiter.api.Assertions.assertThrows(
            NullPointerException.class,
            () ->
                new GuardedToolCallHandler((ex, req) -> null, allow, null, resolver, FIXED_CLOCK)));
  }
}
