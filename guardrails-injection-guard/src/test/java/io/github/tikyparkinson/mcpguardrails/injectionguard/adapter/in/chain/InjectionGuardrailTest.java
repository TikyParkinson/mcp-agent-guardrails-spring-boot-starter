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
package io.github.tikyparkinson.mcpguardrails.injectionguard.adapter.in.chain;

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
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import io.github.tikyparkinson.mcpguardrails.injectionguard.application.port.in.ScanToolArgumentsUseCase;
import io.github.tikyparkinson.mcpguardrails.injectionguard.domain.InjectionSeverity;
import io.github.tikyparkinson.mcpguardrails.injectionguard.domain.ScanResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InjectionGuardrailTest {

  private static final ToolInvocationContext CONTEXT =
      new ToolInvocationContext(
          new AgentId("agent-1"),
          new ToolName("search"),
          Instant.parse("2026-07-24T10:00:00Z"),
          Map.of("q", "text"),
          Map.of());

  private final ScanToolArgumentsUseCase scan = mock(ScanToolArgumentsUseCase.class);
  private final RecordAuditEventUseCase auditBus = mock(RecordAuditEventUseCase.class);
  private final InjectionGuardrail guardrail = new InjectionGuardrail(scan, auditBus);

  @Test
  void shouldAllowWithoutAuditingWhenScanIsClean() {
    // given
    when(scan.scan(Map.of("q", "text"))).thenReturn(new ScanResult(List.of()));

    // when / then: clean pass is silent — no audit noise
    assertEquals(new Allow(), guardrail.evaluate(CONTEXT));
    verifyNoInteractions(auditBus);
  }

  @Test
  void shouldDenyAndAuditWhenMaliciousFindingPresent() {
    // given: one malicious + one suspicious — malicious dominates
    when(scan.scan(Map.of("q", "text")))
        .thenReturn(
            new ScanResult(
                List.of(
                    new ScanResult.Finding("do-anything-now", InjectionSeverity.SUSPICIOUS, "q"),
                    new ScanResult.Finding(
                        "ignore-previous-instructions", InjectionSeverity.MALICIOUS, "q"))));

    // when / then
    assertEquals(
        new Deny(
            "malicious content detected in tool arguments "
                + "(do-anything-now@q, ignore-previous-instructions@q)"),
        guardrail.evaluate(CONTEXT));
    verify(auditBus)
        .record(
            new NewAuditEvent(
                "agent-1",
                "search",
                "injection-guard",
                AuditEventType.DECISION_DENY,
                "do-anything-now@q, ignore-previous-instructions@q"));
  }

  @Test
  void shouldEscalateAndAuditWhenOnlySuspiciousFindingsPresent() {
    // given
    when(scan.scan(Map.of("q", "text")))
        .thenReturn(
            new ScanResult(
                List.of(new ScanResult.Finding("base64-blob", InjectionSeverity.SUSPICIOUS, "q"))));

    // when / then
    assertEquals(
        new Escalate("suspicious content detected in tool arguments (base64-blob@q)"),
        guardrail.evaluate(CONTEXT));
    verify(auditBus)
        .record(
            new NewAuditEvent(
                "agent-1",
                "search",
                "injection-guard",
                AuditEventType.DECISION_ESCALATE,
                "base64-blob@q"));
  }

  @Test
  void shouldPropagateFailureWhenAuditBusThrows() {
    // given: unauditable detection must not pass silently (fail-closed in core)
    when(scan.scan(Map.of("q", "text")))
        .thenReturn(
            new ScanResult(List.of(new ScanResult.Finding("x", InjectionSeverity.MALICIOUS, "q"))));
    when(auditBus.record(any())).thenThrow(new IllegalStateException("audit down"));

    // when / then
    assertThrows(IllegalStateException.class, () -> guardrail.evaluate(CONTEXT));
  }

  @Test
  void shouldExposeStableNameAndOrderWhenQueried() {
    // given / when / then
    assertEquals("injection-guard", guardrail.name());
    assertEquals(50, guardrail.order());
  }

  @Test
  void shouldRejectNullCollaboratorsWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new InjectionGuardrail(null, auditBus));
    assertThrows(NullPointerException.class, () -> new InjectionGuardrail(scan, null));
  }
}
