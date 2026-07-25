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
package io.github.tikyparkinson.mcpguardrails.audit.adapter.in.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.audit.application.port.in.RecordAuditEventUseCase;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEventType;
import io.github.tikyparkinson.mcpguardrails.audit.domain.NewAuditEvent;
import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditGuardrailTest {

  private static final ToolInvocationContext CONTEXT =
      new ToolInvocationContext(
          new AgentId("agent-1"),
          new ToolName("search"),
          Instant.parse("2026-07-24T10:00:00Z"),
          Map.of("q", "secret"),
          Map.of());

  private final RecordAuditEventUseCase bus = mock(RecordAuditEventUseCase.class);
  private final AuditGuardrail guardrail = new AuditGuardrail(bus);

  @Test
  void shouldRecordToolInvokedWithoutArgumentsWhenEvaluating() {
    // given / when
    guardrail.evaluate(CONTEXT);

    // then: event carries identity only — never the tool arguments (PII rule)
    verify(bus)
        .publish(new NewAuditEvent("agent-1", "search", "audit", AuditEventType.TOOL_INVOKED, ""));
  }

  @Test
  void shouldAllowWhenEventRecorded() {
    // given / when / then
    assertEquals(new Allow(), guardrail.evaluate(CONTEXT));
  }

  @Test
  void shouldExposeStableNameAndEarlyOrderWhenQueried() {
    // given / when / then
    assertEquals("audit", guardrail.name());
    assertEquals(-100, guardrail.order());
  }

  @Test
  void shouldPropagateFailureWhenBusThrows() {
    // given: core chain converts this into a fail-closed Deny
    when(bus.publish(any())).thenThrow(new IllegalStateException("store down"));

    // when / then
    assertThrows(IllegalStateException.class, () -> guardrail.evaluate(CONTEXT));
  }

  @Test
  void shouldRejectNullBusWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new AuditGuardrail(null));
  }
}
