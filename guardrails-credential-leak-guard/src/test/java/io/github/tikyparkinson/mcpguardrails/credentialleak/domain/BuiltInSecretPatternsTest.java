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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BuiltInSecretPatternsTest {

  private static RedactedText redact(String text) {
    return SecretRedactor.redact(text, BuiltInSecretPatterns.defaults(), "result.text[0]");
  }

  @Test
  void shouldExposeElevenPatternsWhenAskedForDefaults() {
    // given / when / then
    assertEquals(11, BuiltInSecretPatterns.defaults().size());
  }

  @Test
  void shouldExposeDistinctIdsWhenAskedForDefaults() {
    // given: ids end up in redaction markers, duplicates would be ambiguous
    List<String> ids = BuiltInSecretPatterns.defaults().stream().map(SecretPattern::id).toList();

    // when / then
    assertEquals(ids.size(), ids.stream().distinct().count());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "aws-access-key-id  | key AKIAIOSFODNN7EXAMPLE here",
        "openai-api-key     | token sk-proj-abcdefghij1234567890XY",
        "github-token       | ghp_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "slack-token        | xoxb-1234567890-abcdefghij",
        "google-api-key     | AIzaBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
        "jwt                | eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.dozjgNryP4J3jVm",
        "private-key-block  | -----BEGIN RSA PRIVATE KEY-----",
      })
  void shouldDetectAndRedactWhenSampleContainsKnownCredential(String patternId, String sample) {
    // given / when
    RedactedText redacted = redact(sample);

    // then
    assertEquals(
        List.of(patternId.strip() + "@result.text[0]"),
        redacted.findings().stream().map(SecretFinding::describe).toList());
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource(
      delimiter = '|',
      value = {
        "aws_secret_access_key = wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
            + " | aws_secret_access_key = [REDACTED:aws-secret-access-key]",
        "postgresql://admin:s3cr3t@db:5432/app"
            + " | postgresql://admin:[REDACTED:connection-string-password]@db:5432/app",
        "Authorization: Bearer abcdefghij1234567890"
            + " | Authorization: Bearer [REDACTED:bearer-token]",
        "DB_PASSWORD=hunter2000 | DB_PASSWORD=[REDACTED:credential-assignment]",
      })
  void shouldKeepTheKeyWhenRedactingKeyedCredentials(String sample, String expected) {
    // given: these four patterns must match the key to find the value, but only redact the value
    // when / then
    assertEquals(expected.strip(), redact(sample.strip()).sanitizedText());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "the weather is sunny today",
        "user id 12345 logged in",
        "see https://example.com/docs for help",
        "result: 42 items found",
      })
  void shouldStayQuietWhenTextIsBenign(String benign) {
    // given / when / then
    assertTrue(redact(benign).findings().isEmpty());
  }

  @Test
  void shouldRankKeywordHeuristicAsSuspectedWhenClassifyingPatterns() {
    // given: only the generic assignment pattern is a heuristic; the rest are unmistakable
    List<String> suspected =
        BuiltInSecretPatterns.defaults().stream()
            .filter(pattern -> pattern.severity() == SecretSeverity.SUSPECTED)
            .map(SecretPattern::id)
            .toList();

    // when / then
    assertEquals(List.of("credential-assignment"), suspected);
  }

  @Test
  void shouldLeaveNoTraceOfTheValueWhenRedactingAConnectionString() {
    // given / when
    String sanitized = redact("postgresql://admin:s3cr3t@db:5432/app").sanitizedText();

    // then
    assertFalse(sanitized.contains("s3cr3t"));
  }
}
