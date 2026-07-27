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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
  private final InjectionGuardrail guardrail = new InjectionGuardrail(scan);

  @Test
  void shouldAllowWhenScanIsClean() {
    // given
    when(scan.scan(Map.of("q", "text"))).thenReturn(new ScanResult(List.of()));

    // when / then
    assertEquals(new Allow(), guardrail.evaluate(CONTEXT));
  }

  @Test
  void shouldDenyWhenMaliciousFindingPresent() {
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
  }

  @Test
  void shouldEscalateWhenOnlySuspiciousFindingsPresent() {
    // given
    when(scan.scan(Map.of("q", "text")))
        .thenReturn(
            new ScanResult(
                List.of(new ScanResult.Finding("base64-blob", InjectionSeverity.SUSPICIOUS, "q"))));

    // when / then
    assertEquals(
        new Escalate("suspicious content detected in tool arguments (base64-blob@q)"),
        guardrail.evaluate(CONTEXT));
  }

  @Test
  void shouldNotDependOnTheAuditBusWhenEvaluating() {
    // given a clean scan
    when(scan.scan(Map.of("q", "text"))).thenReturn(new ScanResult(List.of()));

    // when the guardrail evaluates
    // then it decides on its own. ARCHITECTURE.md 5 forbids depending on another guardrail
    // module, so a broken audit store can no longer turn a clean call into an error
    assertEquals(new Allow(), guardrail.evaluate(CONTEXT));
  }

  @Test
  void shouldExposeStableNameAndOrderWhenQueried() {
    // given / when / then
    assertEquals("injection-guard", guardrail.name());
    assertEquals(50, guardrail.order());
  }

  @Test
  void shouldRejectNullCollaboratorWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new InjectionGuardrail(null));
  }

  @Test
  void shouldDenyWhenTheScanCouldNotFinish() {
    // given a scan that found nothing but ran out of budget
    when(scan.scan(Map.of("q", "text"))).thenReturn(new ScanResult(List.of(), false));

    // when the guardrail evaluates
    // then it denies. A walk that stopped early did not clear the arguments — this is the bypass
    // F-10 described, where nine layers of nesting skipped the guardrail entirely
    assertEquals(
        new Deny("tool arguments too large to scan for injection"), guardrail.evaluate(CONTEXT));
  }

  @Test
  void shouldPreferTheSpecificReasonWhenTheScanBothFoundSomethingAndRanOut() {
    // given a scan that matched a rule before running out of budget
    when(scan.scan(Map.of("q", "text")))
        .thenReturn(
            new ScanResult(
                List.of(
                    new ScanResult.Finding(
                        "ignore-previous-instructions", InjectionSeverity.MALICIOUS, "q")),
                false));

    // when the guardrail evaluates
    // then the agent is told what it did wrong, not that the payload was too big
    assertEquals(
        new Deny("malicious content detected in tool arguments (ignore-previous-instructions@q)"),
        guardrail.evaluate(CONTEXT));
  }
}
