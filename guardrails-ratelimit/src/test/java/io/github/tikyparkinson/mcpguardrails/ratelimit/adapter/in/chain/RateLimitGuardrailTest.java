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
package io.github.tikyparkinson.mcpguardrails.ratelimit.adapter.in.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.audit.application.port.in.RecordAuditEventUseCase;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEventType;
import io.github.tikyparkinson.mcpguardrails.audit.domain.NewAuditEvent;
import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import io.github.tikyparkinson.mcpguardrails.ratelimit.application.port.in.CheckRateLimitUseCase;
import io.github.tikyparkinson.mcpguardrails.ratelimit.domain.RateLimitPolicy;
import io.github.tikyparkinson.mcpguardrails.ratelimit.domain.RateLimitStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RateLimitGuardrailTest {

  private static final Instant NOW = Instant.parse("2026-07-24T10:05:30Z");
  private static final RateLimitPolicy POLICY = new RateLimitPolicy(5, Duration.ofMinutes(1));
  private static final ToolInvocationContext CONTEXT =
      new ToolInvocationContext(
          new AgentId("agent-1"), new ToolName("search"), NOW, Map.of(), Map.of());

  private final CheckRateLimitUseCase checkRateLimit = mock(CheckRateLimitUseCase.class);
  private final RecordAuditEventUseCase auditBus = mock(RecordAuditEventUseCase.class);
  private final RateLimitGuardrail guardrail = new RateLimitGuardrail(checkRateLimit, auditBus);

  @Test
  void shouldAllowSilentlyWhenWithinLimit() {
    // given
    when(checkRateLimit.check("agent-1", "search", NOW)).thenReturn(new RateLimitStatus(5, POLICY));

    // when / then: allowed pass is silent — no audit noise
    assertEquals(new Allow(), guardrail.evaluate(CONTEXT));
    verifyNoInteractions(auditBus);
  }

  @Test
  void shouldDenyWithDetailsWhenLimitExceeded() {
    // given
    when(checkRateLimit.check("agent-1", "search", NOW)).thenReturn(new RateLimitStatus(6, POLICY));

    // when / then
    assertEquals(
        new Deny("rate limit exceeded for agent 'agent-1' on tool 'search' (6/5 in PT1M)"),
        guardrail.evaluate(CONTEXT));
    verify(auditBus)
        .publish(
            new NewAuditEvent(
                "agent-1",
                "search",
                "ratelimit",
                AuditEventType.DECISION_DENY,
                "count=6 limit=5 window=PT1M"));
  }

  @Test
  void shouldPropagateFailureWhenAuditBusThrows() {
    // given: an unauditable denial must not pass silently (fail-closed in core)
    when(checkRateLimit.check("agent-1", "search", NOW)).thenReturn(new RateLimitStatus(6, POLICY));
    when(auditBus.publish(any())).thenThrow(new IllegalStateException("audit down"));

    // when / then
    assertThrows(IllegalStateException.class, () -> guardrail.evaluate(CONTEXT));
  }

  @Test
  void shouldExposeStableNameAndLastOrderWhenQueried() {
    // given / when / then
    assertEquals("ratelimit", guardrail.name());
    assertEquals(100, guardrail.order());
  }

  @Test
  void shouldRejectNullCollaboratorsWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new RateLimitGuardrail(null, auditBus));
    assertThrows(NullPointerException.class, () -> new RateLimitGuardrail(checkRateLimit, null));
  }
}
