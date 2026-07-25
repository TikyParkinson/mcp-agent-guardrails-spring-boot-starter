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
package io.github.tikyparkinson.mcpguardrails.toolintegrity.adapter.in.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port.in.VerifyToolIntegrityUseCase;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port.out.ToolDefinitionCatalogPort;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.BaselineEstablished;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.Match;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.Mismatch;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.ToolDefinition;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.ToolFingerprint;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ToolIntegrityGuardrailTest {

  private static final ToolInvocationContext CONTEXT =
      new ToolInvocationContext(
          new AgentId("agent-1"),
          new ToolName("search"),
          Instant.parse("2026-07-25T10:00:00Z"),
          Map.of(),
          Map.of());
  private static final ToolDefinition DEFINITION =
      new ToolDefinition("search", "", "d", Map.of(), Map.of(), Map.of());
  private static final ToolFingerprint EXPECTED = new ToolFingerprint("a".repeat(64));
  private static final ToolFingerprint ACTUAL = new ToolFingerprint("b".repeat(64));

  private final VerifyToolIntegrityUseCase verify = mock(VerifyToolIntegrityUseCase.class);
  private final ToolDefinitionCatalogPort catalog = mock(ToolDefinitionCatalogPort.class);

  private ToolIntegrityGuardrail guardrail(
      MismatchAction onMismatch, UnknownDefinitionAction onUnknown) {
    return new ToolIntegrityGuardrail(verify, catalog, onMismatch, onUnknown);
  }

  @Test
  void shouldAllowWhenBaselineEstablishedOnFirstSight() {
    // given: TOFU first sighting
    when(catalog.findByName("search")).thenReturn(Optional.of(DEFINITION));
    when(verify.verify(DEFINITION)).thenReturn(new BaselineEstablished(ACTUAL));

    // when / then
    assertEquals(
        new Allow(),
        guardrail(MismatchAction.DENY, UnknownDefinitionAction.ALLOW).evaluate(CONTEXT));
  }

  @Test
  void shouldAllowWhenDefinitionMatchesBaseline() {
    // given
    when(catalog.findByName("search")).thenReturn(Optional.of(DEFINITION));
    when(verify.verify(DEFINITION)).thenReturn(new Match(ACTUAL));

    // when / then
    assertEquals(
        new Allow(),
        guardrail(MismatchAction.DENY, UnknownDefinitionAction.ALLOW).evaluate(CONTEXT));
  }

  @Test
  void shouldDenyWithFingerprintsWhenDefinitionDriftsAndActionIsDeny() {
    // given
    when(catalog.findByName("search")).thenReturn(Optional.of(DEFINITION));
    when(verify.verify(DEFINITION)).thenReturn(new Mismatch(EXPECTED, ACTUAL));

    // when / then
    assertEquals(
        new Deny(
            "tool 'search' definition drifted from approved baseline (expected aaaaaaaaaaaa, "
                + "actual bbbbbbbbbbbb); approve the change to proceed"),
        guardrail(MismatchAction.DENY, UnknownDefinitionAction.ALLOW).evaluate(CONTEXT));
  }

  @Test
  void shouldEscalateWhenDefinitionDriftsAndActionIsEscalate() {
    // given
    when(catalog.findByName("search")).thenReturn(Optional.of(DEFINITION));
    when(verify.verify(DEFINITION)).thenReturn(new Mismatch(EXPECTED, ACTUAL));

    // when
    var decision =
        guardrail(MismatchAction.ESCALATE, UnknownDefinitionAction.ALLOW).evaluate(CONTEXT);

    // then
    assertEquals(Escalate.class, decision.getClass());
  }

  @Test
  void shouldAllowSilentlyWhenDefinitionUnknownAndActionIsAllow() {
    // given: tool not registered in the catalog
    when(catalog.findByName("search")).thenReturn(Optional.empty());

    // when / then: nothing to verify — use case never invoked
    assertEquals(
        new Allow(),
        guardrail(MismatchAction.DENY, UnknownDefinitionAction.ALLOW).evaluate(CONTEXT));
    verifyNoInteractions(verify);
  }

  @Test
  void shouldDenyWhenDefinitionUnknownAndActionIsDeny() {
    // given
    when(catalog.findByName("search")).thenReturn(Optional.empty());

    // when / then
    assertEquals(
        new Deny("tool 'search' has no registered definition to verify against"),
        guardrail(MismatchAction.DENY, UnknownDefinitionAction.DENY).evaluate(CONTEXT));
  }

  @Test
  void shouldEscalateWhenDefinitionUnknownAndActionIsEscalate() {
    // given
    when(catalog.findByName("search")).thenReturn(Optional.empty());

    // when / then
    assertEquals(
        new Escalate("tool 'search' has no registered definition to verify against"),
        guardrail(MismatchAction.DENY, UnknownDefinitionAction.ESCALATE).evaluate(CONTEXT));
  }

  @Test
  void shouldExposeStableNameAndEarlyOrderWhenQueried() {
    // given / when
    ToolIntegrityGuardrail g = guardrail(MismatchAction.DENY, UnknownDefinitionAction.ALLOW);

    // then: -50 — trusting the tool precedes any decision about the agent
    assertEquals("tool-integrity", g.name());
    assertEquals(-50, g.order());
  }

  @Test
  void shouldFailWhenUseCaseViolatesContractWithNullResult() {
    // given: a null verdict is a contract violation — it must blow up (chain fails closed),
    // never slip through as an implicit Allow
    when(catalog.findByName("search")).thenReturn(Optional.of(DEFINITION));
    when(verify.verify(DEFINITION)).thenReturn(null);

    // when / then
    assertThrows(
        NullPointerException.class,
        () -> guardrail(MismatchAction.DENY, UnknownDefinitionAction.ALLOW).evaluate(CONTEXT));
  }

  @Test
  void shouldRejectNullCollaboratorsWhenConstructed() {
    // given / when / then
    assertThrows(
        NullPointerException.class,
        () ->
            new ToolIntegrityGuardrail(
                null, catalog, MismatchAction.DENY, UnknownDefinitionAction.ALLOW));
    assertThrows(
        NullPointerException.class,
        () ->
            new ToolIntegrityGuardrail(
                verify, null, MismatchAction.DENY, UnknownDefinitionAction.ALLOW));
    assertThrows(
        NullPointerException.class,
        () -> new ToolIntegrityGuardrail(verify, catalog, null, UnknownDefinitionAction.ALLOW));
    assertThrows(
        NullPointerException.class,
        () -> new ToolIntegrityGuardrail(verify, catalog, MismatchAction.DENY, null));
  }
}
