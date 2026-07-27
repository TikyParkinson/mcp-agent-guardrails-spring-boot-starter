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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SecretScannerTest {

  private static final List<SecretPattern> PATTERNS =
      List.of(
          SecretPattern.of("openai", "sk-[A-Za-z0-9]{10,}", SecretSeverity.CONFIRMED),
          SecretPattern.of("assignment", "(password=)(\\S+)", SecretSeverity.SUSPECTED, 2));

  @Test
  void shouldReportNothingWhenValuesAreClean() {
    // given / when
    SecretScanResult result = SecretScanner.scan(Map.of("q", "weather"), PATTERNS, "arguments");

    // then
    assertTrue(result.clean());
  }

  @Test
  void shouldReportPatternAndLocationWhenValueMatches() {
    // given
    Map<String, Object> values = Map.of("token", "sk-abcdefghij123");

    // when
    SecretScanResult result = SecretScanner.scan(values, PATTERNS, "arguments");

    // then
    assertEquals(
        List.of("openai@arguments.token"),
        result.findings().stream().map(SecretFinding::describe).toList());
  }

  @Test
  void shouldNeverCarryTheDetectedValueWhenReporting() {
    // given: the reason built from findings is read by the model
    Map<String, Object> values = Map.of("token", "sk-abcdefghij123");

    // when
    SecretScanResult result = SecretScanner.scan(values, PATTERNS, "arguments");

    // then
    assertFalse(result.findings().toString().contains("sk-abcdefghij123"));
  }

  @Test
  void shouldPrefixNestedPathsWithTheGivenPrefixWhenScanning() {
    // given
    Map<String, Object> values = Map.of("db", Map.of("conn", List.of("password=hunter2")));

    // when
    SecretScanResult result = SecretScanner.scan(values, PATTERNS, "result.structured");

    // then
    assertEquals(
        List.of("assignment@result.structured.db.conn[0]"),
        result.findings().stream().map(SecretFinding::describe).toList());
  }

  @Test
  void shouldReportEveryMatchingPatternWhenValueHitsSeveral() {
    // given
    Map<String, Object> values = Map.of("cmd", "password=sk-abcdefghij123");

    // when
    SecretScanResult result = SecretScanner.scan(values, PATTERNS, "arguments");

    // then
    assertEquals(2, result.findings().size());
  }

  @Test
  void shouldReportNothingWhenThereAreNoPatterns() {
    // given: an empty pattern set disables detection without failing
    // when
    SecretScanResult result = SecretScanner.scan(Map.of("token", "sk-abc"), List.of(), "arguments");

    // then
    assertTrue(result.clean());
  }

  @Test
  void shouldRejectNullPatternsWhenScanning() {
    // given
    Map<String, Object> values = Map.of();

    // when / then
    assertThrows(NullPointerException.class, () -> SecretScanner.scan(values, null, "arguments"));
  }

  @Test
  void shouldRejectNullPrefixWhenScanning() {
    // given
    Map<String, Object> values = Map.of();

    // when / then
    assertThrows(NullPointerException.class, () -> SecretScanner.scan(values, PATTERNS, null));
  }

  @Test
  void shouldRankConfirmedOverSuspectedWhenBothAreFound() {
    // given
    Map<String, Object> values = Map.of("cmd", "password=sk-abcdefghij123");

    // when
    Optional<SecretSeverity> highest = SecretScanner.scan(values, PATTERNS, "a").highestSeverity();

    // then
    assertEquals(Optional.of(SecretSeverity.CONFIRMED), highest);
  }

  @Test
  void shouldReportNoSeverityWhenResultIsClean() {
    // given / when
    SecretScanResult result = SecretScanner.scan(Map.of(), PATTERNS, "arguments");

    // then
    assertEquals(Optional.empty(), result.highestSeverity());
  }
}
