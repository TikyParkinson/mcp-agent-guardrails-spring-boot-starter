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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DestinationTest {

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
    "API.GitHub.COM, api.github.com",
    "example.com., example.com",
    "example.com..., example.com",
    "'  api.github.com  ', api.github.com",
  })
  void shouldNormalizeCaseAndTrailingDotsWhenBuilt(String raw, String expected) {
    // given / when / then
    assertEquals(expected, Destination.of(raw).value());
  }

  @Test
  void shouldStripBracketsWhenHostIsAnIpv6Literal() {
    // given: URI.getHost() returns "[::1]" while an allowlist is written "::1"
    // when / then
    assertEquals("::1", Destination.of("[::1]").value());
  }

  @Test
  void shouldKeepIpv6AddressWithoutBracketsUnchangedWhenBuilt() {
    // given / when / then
    assertEquals("2001:db8::1", Destination.of("2001:db8::1").value());
  }

  @Test
  void shouldKeepAnUnbalancedBracketWhenOnlyTheOpeningOneIsPresent() {
    // given: brackets are only stripped as a pair, so a malformed value stays as it is and
    // simply fails to match any allowlist entry
    // when / then
    assertEquals("[::1", Destination.of("[::1").value());
  }

  @Test
  void shouldFailWhenHostIsBlank() {
    // given / when / then
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> Destination.of("   "));
    assertEquals("host must not be blank", error.getMessage());
  }

  @Test
  void shouldFailWhenHostIsOnlyDots() {
    // given: normalization would leave nothing behind
    // when / then
    assertThrows(IllegalArgumentException.class, () -> Destination.of("..."));
  }

  @Test
  void shouldRejectNullHostWhenBuilt() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> Destination.of(null));
  }

  @Test
  void shouldRejectNullValueWhenConstructedDirectly() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new Destination(null));
  }

  @Test
  void shouldRejectBlankValueWhenConstructedDirectly() {
    // given / when / then
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> new Destination(" "));
    assertEquals("value must not be blank", error.getMessage());
  }
}
