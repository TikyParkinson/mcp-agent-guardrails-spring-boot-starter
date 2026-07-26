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

import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolInvocationUseCase;
import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolResultUseCase;
import io.github.tikyparkinson.mcpguardrails.core.application.port.out.EscalationResolver;
import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.ApprovedExecution;
import io.github.tikyparkinson.mcpguardrails.core.domain.Block;
import io.github.tikyparkinson.mcpguardrails.core.domain.ChainVerdict;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailEvaluation;
import io.github.tikyparkinson.mcpguardrails.core.domain.PassThrough;
import io.github.tikyparkinson.mcpguardrails.core.domain.Redact;
import io.github.tikyparkinson.mcpguardrails.core.domain.RejectedExecution;
import io.github.tikyparkinson.mcpguardrails.core.domain.ResultVerdict;
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

/** The escalation SPI as seen from the handler that consults it. */
class GuardedToolCallHandlerEscalationTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneOffset.UTC);
  private static final McpSchema.CallToolResult TOOL_RESULT =
      McpSchema.CallToolResult.builder().addTextContent("tool ran").build();
  private static final String ESCALATION_REASON = "anomalous agent behaviour";

  private final McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);

  @Test
  void shouldReturnErrorWithoutRunningToolWhenNoResolverIsRegistered() {
    // given a handler built without an escalation resolver
    BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>
        delegate = mockDelegate();
    GuardedToolCallHandler handler = handler(delegate, null);

    // when an escalated invocation arrives
    McpSchema.CallToolResult result = handler.apply(exchange, request());

    // then the message is exactly the one this handler produced before the SPI existed: the
    // extension has to be invisible to everyone who does not opt in
    assertTrue(result.isError());
    assertEquals(
        "Tool call requires approval (escalated by guardrails): " + ESCALATION_REASON,
        firstText(result));
    verifyNoInteractions(delegate);
  }

  @Test
  void shouldRunTheToolWhenTheResolverApproves() {
    // given a resolver that approves
    GuardedToolCallHandler handler =
        handler((ex, req) -> TOOL_RESULT, (context, verdict) -> new ApprovedExecution("alice"));

    // when an escalated invocation arrives
    McpSchema.CallToolResult result = handler.apply(exchange, request());

    // then the tool runs after all
    assertSame(TOOL_RESULT, result);
  }

  @Test
  void shouldNotRunTheToolWhenTheResolverRejects() {
    // given a resolver that rejects
    BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>
        delegate = mockDelegate();
    GuardedToolCallHandler handler =
        handler(delegate, (context, verdict) -> new RejectedExecution("denied by bob"));

    // when an escalated invocation arrives
    McpSchema.CallToolResult result = handler.apply(exchange, request());

    // then the reason reaches the agent and the tool never ran
    assertTrue(result.isError());
    assertEquals("Tool call not approved: denied by bob", firstText(result));
    verifyNoInteractions(delegate);
  }

  @Test
  void shouldNotRunTheToolWhenTheResolverThrows() {
    // given a resolver whose channel is broken
    BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>
        delegate = mockDelegate();
    GuardedToolCallHandler handler =
        handler(
            delegate,
            (context, verdict) -> {
              throw new IllegalStateException("channel unreachable");
            });

    // when an escalated invocation arrives
    McpSchema.CallToolResult result = handler.apply(exchange, request());

    // then it fails closed: an approval channel that is down must shut the door, not open it
    assertTrue(result.isError());
    assertEquals(
        "Tool call not approved: approval resolver failed: IllegalStateException",
        firstText(result));
    verifyNoInteractions(delegate);
  }

  @Test
  void shouldNotRunTheToolWhenTheResolverReturnsNothing() {
    // given a resolver that answers null
    BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>
        delegate = mockDelegate();
    GuardedToolCallHandler handler = handler(delegate, (context, verdict) -> null);

    // when an escalated invocation arrives
    McpSchema.CallToolResult result = handler.apply(exchange, request());

    // then the absent answer is treated as a rejection rather than dereferenced
    assertTrue(result.isError());
    assertEquals(
        "Tool call not approved: approval resolver returned no outcome", firstText(result));
    verifyNoInteractions(delegate);
  }

  @Test
  void shouldStillApplyTheOutboundChainWhenTheResolverApproves() {
    // given an approved escalation and an outbound chain that redacts
    EvaluateToolResultUseCase redacting =
        context -> new ResultVerdict(new Redact(List.of("[REDACTED]"), "secret found"), List.of());
    GuardedToolCallHandler handler =
        new GuardedToolCallHandler(
            (ex, req) -> TOOL_RESULT,
            escalating(),
            redacting,
            ex -> new AgentId("agent-1"),
            FIXED_CLOCK,
            (context, verdict) -> new ApprovedExecution("alice"));

    // when an escalated invocation arrives
    McpSchema.CallToolResult result = handler.apply(exchange, request());

    // then the result is still sanitized: approving that a tool runs is not approving that its
    // output is seen raw
    assertEquals("[REDACTED]", firstText(result));
  }

  @Test
  void shouldStillBlockTheResultWhenTheOutboundChainSaysSoAfterApproval() {
    // given an approved escalation and an outbound chain that blocks
    EvaluateToolResultUseCase blocking =
        context -> new ResultVerdict(new Block("credential in output"), List.of());
    GuardedToolCallHandler handler =
        new GuardedToolCallHandler(
            (ex, req) -> TOOL_RESULT,
            escalating(),
            blocking,
            ex -> new AgentId("agent-1"),
            FIXED_CLOCK,
            (context, verdict) -> new ApprovedExecution("alice"));

    // when an escalated invocation arrives
    McpSchema.CallToolResult result = handler.apply(exchange, request());

    // then the human approval does not override the outbound guardrails
    assertTrue(result.isError());
    assertEquals("Tool result blocked by guardrails: credential in output", firstText(result));
  }

  @Test
  void shouldHandTheResolverTheWholeVerdictAndTheInvocationContext() {
    // given a resolver that records what it was given
    AtomicReference<ToolInvocationContext> seenContext = new AtomicReference<>();
    AtomicReference<ChainVerdict> seenVerdict = new AtomicReference<>();
    GuardedToolCallHandler handler =
        handler(
            (ex, req) -> TOOL_RESULT,
            (context, verdict) -> {
              seenContext.set(context);
              seenVerdict.set(verdict);
              return new ApprovedExecution("alice");
            });

    // when an escalated invocation arrives
    handler.apply(exchange, request());

    // then it sees every guardrail's evaluation, not just the one that won the combination:
    // whoever decides needs the whole picture
    assertEquals("delete_table", seenContext.get().toolName().value());
    assertEquals(2, seenVerdict.get().evaluations().size());
  }

  private GuardedToolCallHandler handler(
      BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>
          delegate,
      EscalationResolver resolver) {
    return new GuardedToolCallHandler(
        delegate,
        escalating(),
        context -> new ResultVerdict(new PassThrough(), List.of()),
        ex -> new AgentId("agent-1"),
        FIXED_CLOCK,
        resolver);
  }

  private static EvaluateToolInvocationUseCase escalating() {
    return context ->
        new ChainVerdict(
            new Escalate(ESCALATION_REASON),
            List.of(
                new GuardrailEvaluation("anomaly-detector", new Escalate(ESCALATION_REASON)),
                new GuardrailEvaluation("authz", new Allow())));
  }

  @SuppressWarnings("unchecked")
  private static BiFunction<
          McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult>
      mockDelegate() {
    return mock(BiFunction.class);
  }

  private static McpSchema.CallToolRequest request() {
    return new McpSchema.CallToolRequest("delete_table", Map.of("table", "prod"));
  }

  private static String firstText(McpSchema.CallToolResult result) {
    return ((McpSchema.TextContent) result.content().get(0)).text();
  }
}
