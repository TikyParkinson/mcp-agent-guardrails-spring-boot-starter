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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SecretPatternTest {

  @Test
  void shouldCompileCaseInsensitiveWhenBuiltFromRegex() {
    // given / when
    SecretPattern pattern = SecretPattern.of("bearer", "bearer\\s+\\S+", SecretSeverity.CONFIRMED);

    // then
    assertTrue(pattern.pattern().matcher("BEARER abcdef").find());
  }

  @Test
  void shouldDefaultToWholeMatchWhenSecretGroupIsNotGiven() {
    // given / when
    SecretPattern pattern = SecretPattern.of("token", "tok-\\w+", SecretSeverity.CONFIRMED);

    // then
    assertEquals(0, pattern.secretGroup());
  }

  @Test
  void shouldKeepSecretGroupWhenGiven() {
    // given / when
    SecretPattern pattern =
        SecretPattern.of("assignment", "(key=)(\\w+)", SecretSeverity.SUSPECTED, 2);

    // then
    assertEquals(2, pattern.secretGroup());
  }

  @Test
  void shouldFailWhenRegexIsInvalid() {
    // given: a custom pattern coming from configuration may be malformed
    // when / then
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> SecretPattern.of("broken", "([unclosed", SecretSeverity.CONFIRMED));
    assertEquals("invalid regex for pattern broken", error.getMessage());
  }

  @Test
  void shouldFailWhenSecretGroupExceedsCaptureGroups() {
    // given: pointing at a group that does not exist would silently redact nothing
    // when / then
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> SecretPattern.of("mismatch", "(a)(b)", SecretSeverity.CONFIRMED, 3));
    assertEquals(
        "secretGroup 3 exceeds the 2 capture groups of pattern mismatch", error.getMessage());
  }

  @Test
  void shouldFailWhenSecretGroupIsNegative() {
    // given / when / then
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> SecretPattern.of("negative", "(a)", SecretSeverity.CONFIRMED, -1));
    assertEquals("secretGroup must not be negative, was -1", error.getMessage());
  }

  @Test
  void shouldFailWhenIdIsBlank() {
    // given / when / then
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> SecretPattern.of(" ", "a", SecretSeverity.CONFIRMED));
    assertEquals("id must not be blank", error.getMessage());
  }

  @Test
  void shouldRejectNullIdWhenConstructed() {
    // given / when / then
    assertThrows(
        NullPointerException.class, () -> SecretPattern.of(null, "a", SecretSeverity.CONFIRMED));
  }

  @Test
  void shouldRejectNullRegexWhenConstructed() {
    // given / when / then
    assertThrows(
        NullPointerException.class, () -> SecretPattern.of("id", null, SecretSeverity.CONFIRMED));
  }

  @Test
  void shouldRejectNullSeverityWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> SecretPattern.of("id", "a", null));
  }

  @Test
  void shouldRejectNullPatternWhenConstructedDirectly() {
    // given / when / then
    assertThrows(
        NullPointerException.class,
        () -> new SecretPattern("id", null, SecretSeverity.CONFIRMED, 0));
  }

  @Test
  void shouldMatchMetacharactersLiterallyWhenBuiltFromValue() {
    // given: a managed secret may contain regex metacharacters
    String managed = "P@ssw0rd.2026+prod(final)";
    SecretPattern pattern = SecretPattern.ofLiteral("vault:db", managed, SecretSeverity.CONFIRMED);

    // when / then
    assertTrue(pattern.pattern().matcher("using " + managed + " now").find());
    assertFalse(pattern.pattern().matcher("P@ssw0rdX2026+prod(final)").find());
  }

  @Test
  void shouldBeCaseSensitiveWhenBuiltFromValue() {
    // given: secrets distinguish case, relaxing it would only add false positives
    SecretPattern pattern = SecretPattern.ofLiteral("vault:db", "SeCr3T", SecretSeverity.CONFIRMED);

    // when / then
    assertFalse(pattern.pattern().matcher("secr3t").find());
  }

  @Test
  void shouldFailWhenLiteralValueIsBlank() {
    // given / when / then
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> SecretPattern.ofLiteral("vault:empty", "  ", SecretSeverity.CONFIRMED));
    assertEquals("value must not be blank for pattern vault:empty", error.getMessage());
  }

  @Test
  void shouldRejectNullLiteralValueWhenConstructed() {
    // given / when / then
    assertThrows(
        NullPointerException.class,
        () -> SecretPattern.ofLiteral("vault:null", null, SecretSeverity.CONFIRMED));
  }

  @Test
  void shouldUseWholeMatchWhenBuiltFromValue() {
    // given / when
    SecretPattern pattern = SecretPattern.ofLiteral("vault:db", "s3cr3t", SecretSeverity.CONFIRMED);

    // then
    assertEquals(0, pattern.secretGroup());
    assertEquals(Pattern.quote("s3cr3t"), pattern.pattern().pattern());
  }
}
