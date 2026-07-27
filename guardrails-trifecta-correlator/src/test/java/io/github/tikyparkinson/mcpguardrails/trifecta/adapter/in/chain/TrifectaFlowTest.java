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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.core.adapter.in.mcp.GuardedToolCallHandler;
import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import io.github.tikyparkinson.mcpguardrails.trifecta.adapter.out.session.InMemorySessionCapabilityAdapter;
import io.github.tikyparkinson.mcpguardrails.trifecta.application.usecase.AssessTrifectaService;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.Capability;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.SessionId;
import io.github.tikyparkinson.mcpguardrails.trifecta.infrastructure.GuardrailsTrifectaProperties;
import io.github.tikyparkinson.mcpguardrails.trifecta.infrastructure.GuardrailsTrifectaProperties.ToolConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The guardrail wired to the real session store, as the starter would assemble it. */
class TrifectaFlowTest {

  private static final Instant START = Instant.parse("2026-07-26T10:00:00Z");

  @Test
  void shouldEscalateOnTheInvocationThatClosesTheTriangle() {
    // given an agent that reads private data and then untrusted content
    TrifectaGuardrail guardrail = wiring();
    assertInstanceOf(Allow.class, guardrail.evaluate(call("read_customer", "sess-A", 0)));
    assertInstanceOf(Allow.class, guardrail.evaluate(call("fetch_url", "sess-A", 10)));

    // when it then reaches for a tool that can send data out
    GuardrailDecision decision = guardrail.evaluate(call("send_email", "sess-A", 20));

    // then the session is escalated, naming what made it exploitable
    Escalate escalate = assertInstanceOf(Escalate.class, decision);
    assertTrue(escalate.reason().contains("closed by this invocation"), escalate.reason());
  }

  @Test
  void shouldKeepEscalatingTheRestOfTheSession() {
    // given a session whose trifecta already closed
    TrifectaGuardrail guardrail = wiring();
    guardrail.evaluate(call("read_customer", "sess-A", 0));
    guardrail.evaluate(call("fetch_url", "sess-A", 10));
    guardrail.evaluate(call("send_email", "sess-A", 20));

    // when a completely harmless tool is invoked afterwards
    GuardrailDecision decision = guardrail.evaluate(call("get_time", "sess-A", 30));

    // then it escalates too. What is compromised is the arrangement of the session, and letting
    // the next call through because it looks innocent is exactly the gap the framework describes
    assertInstanceOf(Escalate.class, decision);
  }

  @Test
  void shouldNotLetOneUsersSessionEscalateAnother() {
    // given one connection of a client that closed the triangle
    TrifectaGuardrail guardrail = wiring();
    guardrail.evaluate(call("read_customer", "sess-A", 0));
    guardrail.evaluate(call("fetch_url", "sess-A", 10));
    assertInstanceOf(Escalate.class, guardrail.evaluate(call("send_email", "sess-A", 20)));

    // when a different connection of the same client sends an email
    GuardrailDecision other = guardrail.evaluate(call("send_email", "sess-B", 30));

    // then it is allowed. Both invocations carry the agent "copilot": correlating on that would
    // escalate a second person for work the first one did
    assertInstanceOf(Allow.class, other);
  }

  @Test
  void shouldCloseTheTriangleWithATwoLeggedTool() {
    // given a tool that both ingests untrusted content and can send data out
    GuardrailsTrifectaProperties properties =
        new GuardrailsTrifectaProperties(
            true,
            Duration.ofMinutes(30),
            Duration.ofHours(2),
            List.of(
                new ToolConfig("read_customer", Set.of(Capability.PRIVATE_DATA)),
                new ToolConfig(
                    "fetch_url", Set.of(Capability.UNTRUSTED_CONTENT, Capability.EXTERNAL_COMMS))));
    TrifectaGuardrail guardrail = wiring(properties);
    guardrail.evaluate(call("read_customer", "sess-A", 0));

    // when that single tool is invoked
    // then two legs land at once and the triangle closes in two calls rather than three
    assertInstanceOf(Escalate.class, guardrail.evaluate(call("fetch_url", "sess-A", 10)));
  }

  @Test
  void shouldAllowWhenTheSessionWentQuietBeforeClosingTheTriangle() {
    // given two legs seen and then a long silence
    TrifectaGuardrail guardrail = wiring();
    guardrail.evaluate(call("read_customer", "sess-A", 0));
    guardrail.evaluate(call("fetch_url", "sess-A", 10));

    // when the third leg arrives after the idle timeout
    GuardrailDecision decision = guardrail.evaluate(call("send_email", "sess-A", 3_600));

    // then it is a new session: work an hour apart is not the same run
    assertInstanceOf(Allow.class, decision);
  }

  @Test
  void shouldAllowWhenTheSessionHasSimplyRunTooLong() {
    // given a busy agent that closed the triangle and never stops calling
    TrifectaGuardrail guardrail = wiring();
    guardrail.evaluate(call("read_customer", "sess-A", 0));
    guardrail.evaluate(call("fetch_url", "sess-A", 10));
    assertInstanceOf(Escalate.class, guardrail.evaluate(call("send_email", "sess-A", 20)));

    // when the absolute bound passes, with calls in between keeping the idle clock fresh
    for (int second = 600; second < 7_200; second += 600) {
      guardrail.evaluate(call("get_time", "sess-A", second));
    }
    GuardrailDecision decision = guardrail.evaluate(call("get_time", "sess-A", 7_300));

    // then the session finally restarts. Without the absolute bound it never would: every call
    // refreshes the idle clock, so a busy agent would carry one escalation for ever
    assertInstanceOf(Allow.class, decision);
  }

  @Test
  void shouldDetectNothingWhenNoToolIsDeclared() {
    // given a configuration that declares no tool
    TrifectaGuardrail guardrail =
        wiring(
            new GuardrailsTrifectaProperties(
                true, Duration.ofMinutes(30), Duration.ofHours(2), List.of()));

    // when the three most dangerous tools are invoked in one session
    guardrail.evaluate(call("read_customer", "sess-A", 0));
    guardrail.evaluate(call("fetch_url", "sess-A", 10));
    GuardrailDecision decision = guardrail.evaluate(call("send_email", "sess-A", 20));

    // then nothing fires, which is why the module says so at start-up rather than looking healthy
    assertInstanceOf(Allow.class, decision);
  }

  @Test
  void shouldStopEscalatingOnceAPersonResetsTheSession() {
    // given a session with the trifecta closed
    InMemorySessionCapabilityAdapter store =
        new InMemorySessionCapabilityAdapter(Duration.ofMinutes(30), Duration.ofHours(2));
    AssessTrifectaService service = new AssessTrifectaService(store, properties().toPolicy());
    TrifectaGuardrail guardrail =
        new TrifectaGuardrail(service, SessionIdResolver.mcpSessionOrAgent());
    guardrail.evaluate(call("read_customer", "sess-A", 0));
    guardrail.evaluate(call("fetch_url", "sess-A", 10));
    guardrail.evaluate(call("send_email", "sess-A", 20));
    assertEquals(List.of(SessionId.ofMcpSession("sess-A")), service.lockedSessions());

    // when a person reviews it and resets it
    service.reset(SessionId.ofMcpSession("sess-A"));

    // then the agent can work again, which is the only way out short of waiting for expiry
    assertInstanceOf(Allow.class, guardrail.evaluate(call("get_time", "sess-A", 30)));
  }

  private static TrifectaGuardrail wiring() {
    return wiring(properties());
  }

  private static TrifectaGuardrail wiring(GuardrailsTrifectaProperties properties) {
    return new TrifectaGuardrail(
        new AssessTrifectaService(
            new InMemorySessionCapabilityAdapter(
                properties.sessionIdleTimeout(), properties.sessionMaxDuration()),
            properties.toPolicy()),
        SessionIdResolver.mcpSessionOrAgent());
  }

  private static GuardrailsTrifectaProperties properties() {
    return new GuardrailsTrifectaProperties(
        true,
        Duration.ofMinutes(30),
        Duration.ofHours(2),
        List.of(
            new ToolConfig("read_customer", Set.of(Capability.PRIVATE_DATA)),
            new ToolConfig("fetch_url", Set.of(Capability.UNTRUSTED_CONTENT)),
            new ToolConfig("send_email", Set.of(Capability.EXTERNAL_COMMS))));
  }

  private static ToolInvocationContext call(String tool, String sessionId, int secondsIn) {
    return new ToolInvocationContext(
        new AgentId("copilot"),
        new ToolName(tool),
        START.plusSeconds(secondsIn),
        Map.of(),
        Map.of(GuardedToolCallHandler.SESSION_ID, sessionId));
  }
}
