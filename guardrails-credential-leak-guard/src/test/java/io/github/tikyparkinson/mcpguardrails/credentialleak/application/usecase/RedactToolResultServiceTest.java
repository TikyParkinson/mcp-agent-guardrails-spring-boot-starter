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
package io.github.tikyparkinson.mcpguardrails.credentialleak.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.in.ResultRedaction;
import io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.out.SecretPatternSetPort;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretFinding;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretPattern;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretSeverity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RedactToolResultServiceTest {

  private static final SecretPattern TOKEN =
      SecretPattern.of("openai", "sk-[A-Za-z0-9]{10,}", SecretSeverity.CONFIRMED);

  private final SecretPatternSetPort patternSetPort = mock(SecretPatternSetPort.class);
  private final RedactToolResultService service = new RedactToolResultService(patternSetPort);

  @Test
  void shouldReturnTextsUnchangedWhenResultIsClean() {
    // given
    when(patternSetPort.activePatterns()).thenReturn(List.of(TOKEN));

    // when
    ResultRedaction redaction = service.redact(List.of("all good"), Map.of());

    // then
    assertEquals(List.of("all good"), redaction.sanitizedContents());
    assertTrue(redaction.textFindings().isEmpty());
  }

  @Test
  void shouldRedactEveryTextWhenCredentialsAreFound() {
    // given
    when(patternSetPort.activePatterns()).thenReturn(List.of(TOKEN));

    // when
    ResultRedaction redaction =
        service.redact(List.of("first sk-aaaaaaaaaa11", "clean", "sk-bbbbbbbbbb22"), Map.of());

    // then
    assertEquals(
        List.of("first [REDACTED:openai]", "clean", "[REDACTED:openai]"),
        redaction.sanitizedContents());
  }

  @Test
  void shouldReportTextIndexInLocationWhenRedacting() {
    // given
    when(patternSetPort.activePatterns()).thenReturn(List.of(TOKEN));

    // when
    ResultRedaction redaction = service.redact(List.of("clean", "sk-abcdefghij123"), Map.of());

    // then
    assertEquals(
        List.of("openai@result.text[1]"),
        redaction.textFindings().stream().map(SecretFinding::describe).toList());
  }

  @Test
  void shouldKeepStructuredFindingsApartWhenBothHalvesLeak() {
    // given: they lead to different decisions, so they must not be merged
    when(patternSetPort.activePatterns()).thenReturn(List.of(TOKEN));

    // when
    ResultRedaction redaction =
        service.redact(List.of("sk-aaaaaaaaaa11"), Map.of("conn", "sk-bbbbbbbbbb22"));

    // then
    assertEquals(
        List.of("openai@result.text[0]"),
        redaction.textFindings().stream().map(SecretFinding::describe).toList());
    assertEquals(
        List.of("openai@result.structured.conn"),
        redaction.structuredFindings().stream().map(SecretFinding::describe).toList());
  }

  @Test
  void shouldNotRewriteStructuredContentWhenItLeaks() {
    // given: the outbound SPI exposes it read-only, so only the texts come back sanitized
    when(patternSetPort.activePatterns()).thenReturn(List.of(TOKEN));

    // when
    ResultRedaction redaction = service.redact(List.of("clean"), Map.of("conn", "sk-bbbbbbbbbb22"));

    // then
    assertEquals(List.of("clean"), redaction.sanitizedContents());
  }

  @Test
  void shouldKeepTheSizeOfTheContentListWhenRedacting() {
    // given: the Redact contract of core requires a positional, same-size replacement
    when(patternSetPort.activePatterns()).thenReturn(List.of(TOKEN));
    List<String> texts = List.of("sk-aaaaaaaaaa11", "clean", "sk-bbbbbbbbbb22");

    // when
    ResultRedaction redaction = service.redact(texts, Map.of());

    // then
    assertEquals(texts.size(), redaction.sanitizedContents().size());
  }

  @Test
  void shouldReturnEmptyContentsWhenResultHasNoTexts() {
    // given
    when(patternSetPort.activePatterns()).thenReturn(List.of(TOKEN));

    // when
    ResultRedaction redaction = service.redact(List.of(), Map.of());

    // then
    assertEquals(List.of(), redaction.sanitizedContents());
  }

  @Test
  void shouldRejectNullTextContentsWhenRedacting() {
    // given
    Map<String, Object> structured = Map.of();

    // when / then
    assertThrows(NullPointerException.class, () -> service.redact(null, structured));
  }

  @Test
  void shouldRejectNullStructuredContentWhenRedacting() {
    // given
    List<String> texts = List.of();

    // when / then
    assertThrows(NullPointerException.class, () -> service.redact(texts, null));
  }

  @Test
  void shouldRejectNullPortWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new RedactToolResultService(null));
  }
}
