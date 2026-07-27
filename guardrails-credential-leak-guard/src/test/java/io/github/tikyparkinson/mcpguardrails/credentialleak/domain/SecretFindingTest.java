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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SecretFindingTest {

  @Test
  void shouldJoinPatternAndLocationWhenDescribed() {
    // given / when
    SecretFinding finding =
        new SecretFinding("openai-api-key", SecretSeverity.CONFIRMED, "arguments.token");

    // then
    assertEquals("openai-api-key@arguments.token", finding.describe());
  }

  @Test
  void shouldRejectNullPatternIdWhenConstructed() {
    // given / when / then
    assertThrows(
        NullPointerException.class,
        () -> new SecretFinding(null, SecretSeverity.CONFIRMED, "arguments.token"));
  }

  @Test
  void shouldRejectNullSeverityWhenConstructed() {
    // given / when / then
    assertThrows(
        NullPointerException.class, () -> new SecretFinding("id", null, "arguments.token"));
  }

  @Test
  void shouldRejectNullLocationWhenConstructed() {
    // given / when / then
    assertThrows(
        NullPointerException.class, () -> new SecretFinding("id", SecretSeverity.CONFIRMED, null));
  }

  @Test
  void shouldRejectBlankPatternIdWhenConstructed() {
    // given / when / then
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> new SecretFinding(" ", SecretSeverity.CONFIRMED, "arguments.token"));
    assertEquals("patternId must not be blank", error.getMessage());
  }

  @Test
  void shouldRejectBlankLocationWhenConstructed() {
    // given: a finding without a location cannot be acted upon
    // when / then
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> new SecretFinding("id", SecretSeverity.CONFIRMED, ""));
    assertEquals("location must not be blank", error.getMessage());
  }

  @Test
  void shouldCopyFindingsDefensivelyWhenScanResultConstructed() {
    // given
    List<SecretFinding> mutable =
        new ArrayList<>(List.of(new SecretFinding("id", SecretSeverity.CONFIRMED, "arguments.a")));
    SecretScanResult result = new SecretScanResult(mutable);

    // when
    mutable.add(new SecretFinding("other", SecretSeverity.CONFIRMED, "arguments.b"));

    // then
    assertEquals(1, result.findings().size());
  }

  @Test
  void shouldRejectNullFindingsWhenScanResultConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new SecretScanResult(null));
  }

  @Test
  void shouldCopyFindingsDefensivelyWhenRedactedTextConstructed() {
    // given
    List<SecretFinding> mutable = new ArrayList<>();
    RedactedText redacted = new RedactedText("text", mutable);

    // when
    mutable.add(new SecretFinding("id", SecretSeverity.CONFIRMED, "arguments.a"));

    // then
    assertEquals(List.of(), redacted.findings());
  }

  @Test
  void shouldRejectNullSanitizedTextWhenRedactedTextConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new RedactedText(null, List.of()));
  }

  @Test
  void shouldRejectNullFindingsWhenRedactedTextConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new RedactedText("text", null));
  }

  @Test
  void shouldRejectNullPathWhenFlattenedValueConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new FlattenedValue(null, "text"));
  }

  @Test
  void shouldRejectNullTextWhenFlattenedValueConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new FlattenedValue("path", null));
  }
}
