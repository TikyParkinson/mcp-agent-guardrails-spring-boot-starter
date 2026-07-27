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
package io.github.tikyparkinson.mcpguardrails.credentialleak.adapter.in.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.in.ScanToolArgumentsForSecretsUseCase;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretFinding;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretScanResult;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretSeverity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CredentialLeakGuardrailTest {

  private static final ToolInvocationContext CONTEXT =
      new ToolInvocationContext(
          new AgentId("copilot"),
          new ToolName("deploy"),
          Instant.parse("2026-07-26T10:00:00Z"),
          Map.of("token", "sk-live-1"),
          Map.of());

  private final ScanToolArgumentsForSecretsUseCase useCase =
      mock(ScanToolArgumentsForSecretsUseCase.class);

  private CredentialLeakGuardrail guardrail(InputAction onConfirmed, InputAction onSuspected) {
    return new CredentialLeakGuardrail(useCase, onConfirmed, onSuspected);
  }

  private static SecretScanResult found(SecretSeverity severity, String patternId) {
    return new SecretScanResult(List.of(new SecretFinding(patternId, severity, "arguments.token")));
  }

  @Test
  void shouldAllowWhenArgumentsAreClean() {
    // given
    when(useCase.scan(CONTEXT.arguments())).thenReturn(new SecretScanResult(List.of()));

    // when
    GuardrailDecision decision =
        guardrail(InputAction.DENY, InputAction.ESCALATE).evaluate(CONTEXT);

    // then
    assertInstanceOf(Allow.class, decision);
  }

  @Test
  void shouldDenyWhenConfirmedCredentialIsFoundAndPolicyIsDeny() {
    // given
    when(useCase.scan(CONTEXT.arguments()))
        .thenReturn(found(SecretSeverity.CONFIRMED, "openai-api-key"));

    // when
    GuardrailDecision decision =
        guardrail(InputAction.DENY, InputAction.ESCALATE).evaluate(CONTEXT);

    // then
    assertEquals(
        "credential detected in tool arguments (openai-api-key@arguments.token)",
        assertInstanceOf(Deny.class, decision).reason());
  }

  @Test
  void shouldEscalateWhenConfirmedCredentialIsFoundAndPolicyIsEscalate() {
    // given
    when(useCase.scan(CONTEXT.arguments()))
        .thenReturn(found(SecretSeverity.CONFIRMED, "openai-api-key"));

    // when
    GuardrailDecision decision =
        guardrail(InputAction.ESCALATE, InputAction.ESCALATE).evaluate(CONTEXT);

    // then
    assertInstanceOf(Escalate.class, decision);
  }

  @Test
  void shouldEscalateWhenOnlySuspectedCredentialIsFound() {
    // given: a keyword heuristic should not silently kill a legitimate call
    when(useCase.scan(CONTEXT.arguments()))
        .thenReturn(found(SecretSeverity.SUSPECTED, "credential-assignment"));

    // when
    GuardrailDecision decision =
        guardrail(InputAction.DENY, InputAction.ESCALATE).evaluate(CONTEXT);

    // then
    assertEquals(
        "credential detected in tool arguments (credential-assignment@arguments.token)",
        assertInstanceOf(Escalate.class, decision).reason());
  }

  @Test
  void shouldDenyWhenSuspectedCredentialIsFoundAndPolicyIsDeny() {
    // given
    when(useCase.scan(CONTEXT.arguments()))
        .thenReturn(found(SecretSeverity.SUSPECTED, "credential-assignment"));

    // when
    GuardrailDecision decision = guardrail(InputAction.DENY, InputAction.DENY).evaluate(CONTEXT);

    // then
    assertInstanceOf(Deny.class, decision);
  }

  @Test
  void shouldUseTheConfirmedPolicyWhenBothSeveritiesAreFound() {
    // given: the highest severity drives the decision
    when(useCase.scan(CONTEXT.arguments()))
        .thenReturn(
            new SecretScanResult(
                List.of(
                    new SecretFinding("credential-assignment", SecretSeverity.SUSPECTED, "a.b"),
                    new SecretFinding("openai-api-key", SecretSeverity.CONFIRMED, "a.c"))));

    // when
    GuardrailDecision decision =
        guardrail(InputAction.DENY, InputAction.ESCALATE).evaluate(CONTEXT);

    // then
    assertInstanceOf(Deny.class, decision);
  }

  @Test
  void shouldListEveryFindingOnceWhenSeveralAreReported() {
    // given: the same pattern hitting the same place twice adds no information
    when(useCase.scan(CONTEXT.arguments()))
        .thenReturn(
            new SecretScanResult(
                List.of(
                    new SecretFinding("openai-api-key", SecretSeverity.CONFIRMED, "arguments.a"),
                    new SecretFinding("openai-api-key", SecretSeverity.CONFIRMED, "arguments.a"),
                    new SecretFinding("jwt", SecretSeverity.CONFIRMED, "arguments.b"))));

    // when
    GuardrailDecision decision =
        guardrail(InputAction.DENY, InputAction.ESCALATE).evaluate(CONTEXT);

    // then
    assertEquals(
        "credential detected in tool arguments (openai-api-key@arguments.a, jwt@arguments.b)",
        assertInstanceOf(Deny.class, decision).reason());
  }

  @Test
  void shouldNeverPutTheValueInTheReasonWhenDenying() {
    // given: the reason reaches the model, so it must not carry the secret
    when(useCase.scan(CONTEXT.arguments()))
        .thenReturn(found(SecretSeverity.CONFIRMED, "openai-api-key"));

    // when
    GuardrailDecision decision =
        guardrail(InputAction.DENY, InputAction.ESCALATE).evaluate(CONTEXT);

    // then
    assertFalse(assertInstanceOf(Deny.class, decision).reason().contains("sk-live-1"));
  }

  @Test
  void shouldRunAfterInjectionGuardWhenOrderedInTheChain() {
    // given / when
    CredentialLeakGuardrail g = guardrail(InputAction.DENY, InputAction.ESCALATE);

    // then
    assertEquals("credential-leak", g.name());
    assertEquals(60, g.order());
  }

  @Test
  void shouldRejectNullUseCaseWhenConstructed() {
    // given / when / then
    assertThrows(
        NullPointerException.class,
        () -> new CredentialLeakGuardrail(null, InputAction.DENY, InputAction.ESCALATE));
  }

  @Test
  void shouldRejectNullConfirmedActionWhenConstructed() {
    // given / when / then
    assertThrows(
        NullPointerException.class,
        () -> new CredentialLeakGuardrail(useCase, null, InputAction.ESCALATE));
  }

  @Test
  void shouldRejectNullSuspectedActionWhenConstructed() {
    // given / when / then
    assertThrows(
        NullPointerException.class,
        () -> new CredentialLeakGuardrail(useCase, InputAction.DENY, null));
  }

  @Test
  void shouldDenyWhenTheScanCouldNotFinish() {
    // given a scan that found nothing but ran out of budget
    when(useCase.scan(CONTEXT.arguments())).thenReturn(new SecretScanResult(List.of(), false));

    // when the guardrail evaluates
    // then it denies. Answering Allow would claim the arguments are clean when the truth is that
    // nobody finished looking at them — this is the bypass F-10 described
    assertEquals(
        new Deny("tool arguments too large to scan for credentials"),
        guardrail(InputAction.DENY, InputAction.ESCALATE).evaluate(CONTEXT));
  }

  @Test
  void shouldPreferTheSpecificReasonWhenTheScanBothFoundSomethingAndRanOut() {
    // given a scan that found a credential before running out of budget
    when(useCase.scan(CONTEXT.arguments()))
        .thenReturn(
            new SecretScanResult(
                List.of(new SecretFinding("aws-access-key-id", SecretSeverity.CONFIRMED, "a.b")),
                false));

    // when the guardrail evaluates
    // then the agent is told what it did wrong, not that the payload was too big. The generic
    // reason would invite it to retry with a different shape
    assertEquals(
        new Deny("credential detected in tool arguments (aws-access-key-id@a.b)"),
        guardrail(InputAction.DENY, InputAction.ESCALATE).evaluate(CONTEXT));
  }
}
