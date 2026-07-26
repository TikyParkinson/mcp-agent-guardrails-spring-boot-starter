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
package io.github.tikyparkinson.mcpguardrails.egress.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AllowedDestinationTest {

  private static boolean wildcardAccepts(String host) {
    return AllowedDestination.of("*.example.com").matches(Destination.of(host));
  }

  @Test
  void shouldAcceptOnlyTheSameHostWhenPatternIsExact() {
    // given
    AllowedDestination exact = AllowedDestination.of("api.github.com");

    // when / then
    assertTrue(exact.matches(Destination.of("api.github.com")));
    assertFalse(exact.matches(Destination.of("other.github.com")));
  }

  @Test
  void shouldIgnoreCaseAndTrailingDotWhenMatchingAnExactPattern() {
    // given: both sides go through the same normalization
    AllowedDestination exact = AllowedDestination.of("API.GitHub.COM.");

    // when / then
    assertTrue(exact.matches(Destination.of("api.github.com")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"a.example.com", "a.b.example.com", "deep.nested.example.com"})
  void shouldAcceptSubdomainsWhenPatternIsAWildcard(String host) {
    // given / when / then
    assertTrue(wildcardAccepts(host));
  }

  @Test
  void shouldRejectTheApexWhenPatternIsAWildcard() {
    // given: as with certificates and cookies, *.example.com does not include example.com
    // when / then
    assertFalse(wildcardAccepts("example.com"));
  }

  @Test
  void shouldRejectAHostThatIsOnlyTheWildcardSuffixWhenMatching() {
    // given: ".example.com" ends with the suffix but has no subdomain label in front of it
    // when / then
    assertFalse(wildcardAccepts(".example.com"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"example.com.evil.com", "notexample.com", "myexample.com"})
  void shouldRejectLookalikeHostsWhenPatternIsAWildcard(String host) {
    // given: a naive endsWith would accept notexample.com, which is the bypass to avoid
    // when / then
    assertFalse(wildcardAccepts(host));
  }

  @Test
  void shouldFailWhenWildcardHasNoHostAfterIt() {
    // given: "*." normalizes to a bare "*", which is rejected as a widening pattern
    // when / then
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> AllowedDestination.of("*."));
    assertEquals(
        "'*' is only allowed as the leading label of a pattern, was: *.", error.getMessage());
  }

  @Test
  void shouldFailWhenWildcardAppearsTwice() {
    // given / when / then
    assertThrows(IllegalArgumentException.class, () -> AllowedDestination.of("*.*.example.com"));
  }

  @Test
  void shouldFailWhenWildcardIsNotTheLeadingLabel() {
    // given: "example.*" or a bare "*" would silently widen the allowlist
    // when / then
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> AllowedDestination.of("example.*"));
    assertEquals(
        "'*' is only allowed as the leading label of a pattern, was: example.*",
        error.getMessage());
  }

  @Test
  void shouldFailWhenPatternIsABareWildcard() {
    // given: allowing everything must not be expressible by accident
    // when / then
    assertThrows(IllegalArgumentException.class, () -> AllowedDestination.of("*"));
  }

  @Test
  void shouldFailWhenPatternIsBlank() {
    // given / when / then
    assertThrows(IllegalArgumentException.class, () -> AllowedDestination.of("  "));
  }

  @Test
  void shouldFailWhenPatternIsOnlyDots() {
    // given: normalization leaves nothing to match against
    // when / then
    assertThrows(IllegalArgumentException.class, () -> AllowedDestination.of("..."));
  }

  @Test
  void shouldRejectNullPatternWhenParsed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> AllowedDestination.of(null));
  }

  @Test
  void shouldRejectNullPatternWhenConstructedDirectly() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new AllowedDestination(null, false));
  }

  @Test
  void shouldRejectBlankPatternWhenConstructedDirectly() {
    // given / when / then
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> new AllowedDestination(" ", false));
    assertEquals("pattern must not be blank", error.getMessage());
  }

  @Test
  void shouldRejectNullDestinationWhenMatching() {
    // given
    AllowedDestination exact = AllowedDestination.of("api.github.com");

    // when / then
    assertThrows(NullPointerException.class, () -> exact.matches(null));
  }
}
