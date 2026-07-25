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
package io.github.tikyparkinson.mcpguardrails.credentialleak.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SecretRedactorTest {

  private static final SecretPattern TOKEN =
      SecretPattern.of("openai", "sk-[A-Za-z0-9]{10,}", SecretSeverity.CONFIRMED);
  private static final SecretPattern ASSIGNMENT =
      SecretPattern.of("assignment", "(password=)(\\S+)", SecretSeverity.SUSPECTED, 2);

  private static String redactedText(String text, List<SecretPattern> patterns) {
    return SecretRedactor.redact(text, patterns, "result.text[0]").sanitizedText();
  }

  @Test
  void shouldReturnTextUnchangedWhenNothingMatches() {
    // given / when
    RedactedText redacted = SecretRedactor.redact("all good", List.of(TOKEN), "result.text[0]");

    // then
    assertEquals("all good", redacted.sanitizedText());
    assertTrue(redacted.findings().isEmpty());
  }

  @Test
  void shouldReplaceWholeMatchWhenPatternHasNoSecretGroup() {
    // given / when / then
    assertEquals(
        "token [REDACTED:openai] used",
        redactedText("token sk-abcdefghij123 used", List.of(TOKEN)));
  }

  @Test
  void shouldKeepTheKeyWhenPatternDeclaresASecretGroup() {
    // given: redacting the whole match would destroy the surrounding structure
    // when / then
    assertEquals(
        "password=[REDACTED:assignment]", redactedText("password=hunter2000", List.of(ASSIGNMENT)));
  }

  @Test
  void shouldLeaveNoTraceOfTheValueWhenRedacting() {
    // given
    String text = "key sk-abcdefghij123 here";

    // when
    String sanitized = redactedText(text, List.of(TOKEN));

    // then
    assertFalse(sanitized.contains("sk-abcdefghij123"));
  }

  @Test
  void shouldReplaceEveryOccurrenceWhenPatternMatchesSeveralTimes() {
    // given / when / then
    assertEquals(
        "[REDACTED:openai] and [REDACTED:openai]",
        redactedText("sk-aaaaaaaaaa11 and sk-bbbbbbbbbb22", List.of(TOKEN)));
  }

  @Test
  void shouldApplyEveryPatternWhenSeveralAreActive() {
    // given / when / then
    assertEquals(
        "[REDACTED:openai] password=[REDACTED:assignment]",
        redactedText("sk-abcdefghij123 password=hunter2000", List.of(TOKEN, ASSIGNMENT)));
  }

  @Test
  void shouldReportOneFindingPerOccurrenceWhenRedacting() {
    // given / when
    RedactedText redacted =
        SecretRedactor.redact(
            "sk-aaaaaaaaaa11 and sk-bbbbbbbbbb22", List.of(TOKEN), "result.text[3]");

    // then
    assertEquals(
        List.of("openai@result.text[3]", "openai@result.text[3]"),
        redacted.findings().stream().map(SecretFinding::describe).toList());
  }

  @Test
  void shouldFallBackToWholeMatchWhenSecretGroupDidNotParticipate() {
    // given: a custom pattern with an optional group must never leave the value in place
    SecretPattern optionalGroup =
        SecretPattern.of("optional", "secret-(x)?([a-z0-9]{6,})", SecretSeverity.CONFIRMED, 1);

    // when
    String sanitized = redactedText("value secret-abc123 end", List.of(optionalGroup));

    // then
    assertEquals("value [REDACTED:optional] end", sanitized);
  }

  @Test
  void shouldNotRematchAlreadyRedactedTextWhenPatternsOverlap() {
    // given: the second pattern sees what the first one already sanitized
    SecretPattern broad = SecretPattern.of("broad", "sk-\\S+", SecretSeverity.CONFIRMED);

    // when
    String sanitized = redactedText("sk-abcdefghij123", List.of(TOKEN, broad));

    // then
    assertEquals("[REDACTED:openai]", sanitized);
  }

  @Test
  void shouldReturnTextUnchangedWhenThereAreNoPatterns() {
    // given
    String text = "password=hunter2000";

    // when / then
    assertSame(text, redactedText(text, List.of()));
  }

  @Test
  void shouldRejectNullTextWhenRedacting() {
    // given
    List<SecretPattern> patterns = List.of(TOKEN);

    // when / then
    assertThrows(
        NullPointerException.class, () -> SecretRedactor.redact(null, patterns, "location"));
  }

  @Test
  void shouldRejectNullPatternsWhenRedacting() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> SecretRedactor.redact("text", null, "location"));
  }

  @Test
  void shouldRejectNullLocationWhenRedacting() {
    // given
    List<SecretPattern> patterns = List.of(TOKEN);

    // when / then
    assertThrows(NullPointerException.class, () -> SecretRedactor.redact("text", patterns, null));
  }
}
