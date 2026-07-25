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
import io.github.tikyparkinson.mcpguardrails.core.domain.Block;
import io.github.tikyparkinson.mcpguardrails.core.domain.PassThrough;
import io.github.tikyparkinson.mcpguardrails.core.domain.Redact;
import io.github.tikyparkinson.mcpguardrails.core.domain.ResultDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolResultContext;
import io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.in.RedactToolResultUseCase;
import io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.in.ResultRedaction;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretFinding;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretSeverity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CredentialLeakResultGuardrailTest {

  private static final ToolResultContext CONTEXT =
      new ToolResultContext(
          new AgentId("copilot"),
          new ToolName("read_file"),
          Instant.parse("2026-07-26T10:00:00Z"),
          List.of("API_KEY=sk-live-1"),
          Map.of(),
          false);

  private final RedactToolResultUseCase useCase = mock(RedactToolResultUseCase.class);

  private CredentialLeakResultGuardrail guardrail(OutputAction onTextLeak) {
    return new CredentialLeakResultGuardrail(useCase, onTextLeak);
  }

  private static SecretFinding finding(String patternId, String location) {
    return new SecretFinding(patternId, SecretSeverity.CONFIRMED, location);
  }

  private void redactionReturns(ResultRedaction redaction) {
    when(useCase.redact(CONTEXT.textContents(), CONTEXT.structuredContent())).thenReturn(redaction);
  }

  @Test
  void shouldPassThroughWhenResultIsClean() {
    // given
    redactionReturns(new ResultRedaction(List.of("API_KEY=sk-live-1"), List.of(), List.of()));

    // when
    ResultDecision decision = guardrail(OutputAction.REDACT).inspect(CONTEXT);

    // then
    assertInstanceOf(PassThrough.class, decision);
  }

  @Test
  void shouldRedactWhenTextLeaksAndPolicyIsRedact() {
    // given
    redactionReturns(
        new ResultRedaction(
            List.of("API_KEY=[REDACTED:openai-api-key]"),
            List.of(finding("openai-api-key", "result.text[0]")),
            List.of()));

    // when
    ResultDecision decision = guardrail(OutputAction.REDACT).inspect(CONTEXT);

    // then
    Redact redact = assertInstanceOf(Redact.class, decision);
    assertEquals(List.of("API_KEY=[REDACTED:openai-api-key]"), redact.sanitizedContents());
    assertEquals("openai-api-key@result.text[0]", redact.reason());
  }

  @Test
  void shouldBlockWhenTextLeaksAndPolicyIsBlock() {
    // given
    redactionReturns(
        new ResultRedaction(
            List.of("API_KEY=[REDACTED:openai-api-key]"),
            List.of(finding("openai-api-key", "result.text[0]")),
            List.of()));

    // when
    ResultDecision decision = guardrail(OutputAction.BLOCK).inspect(CONTEXT);

    // then
    assertEquals(
        "credential detected in tool result (openai-api-key@result.text[0])",
        assertInstanceOf(Block.class, decision).reason());
  }

  @Test
  void shouldBlockWhenStructuredContentLeaksEvenIfPolicyIsRedact() {
    // given: structured content cannot be handed back sanitized, so it is always fail-closed
    redactionReturns(
        new ResultRedaction(
            List.of("API_KEY=sk-live-1"),
            List.of(),
            List.of(finding("connection-string-password", "result.structured.conn"))));

    // when
    ResultDecision decision = guardrail(OutputAction.REDACT).inspect(CONTEXT);

    // then
    assertEquals(
        "credential detected in structured result "
            + "(connection-string-password@result.structured.conn); "
            + "structured content cannot be redacted",
        assertInstanceOf(Block.class, decision).reason());
  }

  @Test
  void shouldBlockOnStructuredLeakWhenTheTextLeaksTooShouldNotDowngradeIt() {
    // given: a redactable text must not turn a non-redactable leak into a Redact
    redactionReturns(
        new ResultRedaction(
            List.of("API_KEY=[REDACTED:openai-api-key]"),
            List.of(finding("openai-api-key", "result.text[0]")),
            List.of(finding("jwt", "result.structured.token"))));

    // when
    ResultDecision decision = guardrail(OutputAction.REDACT).inspect(CONTEXT);

    // then
    assertInstanceOf(Block.class, decision);
  }

  @Test
  void shouldNeverPutTheValueInTheReasonWhenBlocking() {
    // given
    redactionReturns(
        new ResultRedaction(
            List.of("API_KEY=sk-live-1"),
            List.of(),
            List.of(finding("openai-api-key", "result.structured.key"))));

    // when
    ResultDecision decision = guardrail(OutputAction.REDACT).inspect(CONTEXT);

    // then
    assertFalse(assertInstanceOf(Block.class, decision).reason().contains("sk-live-1"));
  }

  @Test
  void shouldUseDefaultOrderWhenPlacedInTheOutboundChain() {
    // given / when
    CredentialLeakResultGuardrail g = guardrail(OutputAction.REDACT);

    // then
    assertEquals("credential-leak", g.name());
    assertEquals(0, g.order());
  }

  @Test
  void shouldRejectNullUseCaseWhenConstructed() {
    // given / when / then
    assertThrows(
        NullPointerException.class,
        () -> new CredentialLeakResultGuardrail(null, OutputAction.REDACT));
  }

  @Test
  void shouldRejectNullOutputActionWhenConstructed() {
    // given / when / then
    assertThrows(
        NullPointerException.class, () -> new CredentialLeakResultGuardrail(useCase, null));
  }
}
