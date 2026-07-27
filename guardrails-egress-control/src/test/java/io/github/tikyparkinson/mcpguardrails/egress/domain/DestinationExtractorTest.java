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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DestinationExtractorTest {

  private static String extractedHost(String rawValue) {
    return assertInstanceOf(Extracted.class, DestinationExtractor.extract(rawValue))
        .destination()
        .value();
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
    "https://api.github.com/repos, api.github.com",
    "http://api.github.com:8443/x, api.github.com",
    "https://API.GitHub.COM./x, api.github.com",
  })
  void shouldTakeTheHostWhenValueIsAnUrl(String url, String expected) {
    // given / when / then
    assertEquals(expected, extractedHost(url));
  }

  @Test
  void shouldIgnoreUserinfoWhenUrlLooksLikeAnotherHost() {
    // given: the classic trap against a hand-rolled parser
    // when / then
    assertEquals("good.com", extractedHost("https://evil.com@good.com/x"));
  }

  @Test
  void shouldStripBracketsWhenUrlHostIsIpv6() {
    // given / when / then
    assertEquals("::1", extractedHost("http://[::1]:8080/p"));
  }

  @Test
  void shouldTakeTheDomainWhenValueIsAnEmailAddress() {
    // given / when / then
    assertEquals("corp.example.com", extractedHost("user@corp.example.com"));
  }

  @Test
  void shouldTakeTheLastDomainWhenEmailContainsSeveralAtSigns() {
    // given: only what follows the last '@' is the real domain
    // when / then
    assertEquals("real.com", extractedHost("a@b@real.com"));
  }

  @Test
  void shouldTakeTheValueWhenItIsABareHost() {
    // given / when / then
    assertEquals("api.internal.corp", extractedHost("api.internal.corp"));
  }

  @Test
  void shouldTakeTheValueWhenItIsAnIpLiteral() {
    // given / when / then
    assertEquals("192.168.1.1", extractedHost("192.168.1.1"));
  }

  @Test
  void shouldNotDetermineWhenHostIsInternationalized() {
    // given: URI.getHost() yields null for non-ASCII hosts, so both a homograph and a legitimate
    // IDN are denied — a documented limitation of this module
    // when / then
    assertInstanceOf(
        NotDeterminable.class, DestinationExtractor.extract("https://jos\u00e9.example.com/"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"not a url at all", "javascript:alert(1)", "-bad-.com", "nope!"})
  void shouldNotDetermineWhenValueIsNotAHost(String value) {
    // given / when / then
    assertInstanceOf(NotDeterminable.class, DestinationExtractor.extract(value));
  }

  @ParameterizedTest
  @ValueSource(strings = {"localhost", "intranet"})
  void shouldAcceptSingleLabelHostsWhenValueHasNoDots(String value) {
    // given: single-label hosts are legitimate on internal networks
    // when / then
    assertEquals(value, extractedHost(value));
  }

  @Test
  void shouldAcceptABareIpv6AddressWhenValueIsNotAnUrl() {
    // given / when / then
    assertEquals("2001:db8::1", extractedHost("2001:db8::1"));
  }

  @Test
  void shouldNotDetermineWhenValueIsOnlyAnAtSign() {
    // given: nothing follows the '@', so there is no domain to read
    // when / then
    assertInstanceOf(NotDeterminable.class, DestinationExtractor.extract("@"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void shouldReportEmptyWhenValueHasNoContent(String value) {
    // given / when
    NotDeterminable result =
        assertInstanceOf(NotDeterminable.class, DestinationExtractor.extract(value));

    // then
    assertEquals("empty", result.rawValueKind());
  }

  @Test
  void shouldReportNotAHostWhenValueCannotBeParsed() {
    // given / when
    NotDeterminable result =
        assertInstanceOf(NotDeterminable.class, DestinationExtractor.extract("nope!"));

    // then
    assertEquals("not-a-host", result.rawValueKind());
  }

  @Test
  void shouldRejectNullValueWhenExtracting() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> DestinationExtractor.extract(null));
  }

  @Test
  void shouldSurviveAHostWithThousandsOfLabelsWhenExtracting() {
    // given: argument values are attacker-controlled, and a nested repetition would make the
    // regex engine recurse once per label until it raises a StackOverflowError — an Error the
    // guardrail chain does not catch
    String many = "a.".repeat(20_000) + "!";

    // when / then
    assertInstanceOf(NotDeterminable.class, DestinationExtractor.extract(many));
  }

  @Test
  void shouldSurviveAVeryLongSingleLabelWhenExtracting() {
    // given
    String hostileLabel = "a".repeat(100_000) + "!";

    // when / then
    assertInstanceOf(NotDeterminable.class, DestinationExtractor.extract(hostileLabel));
  }

  @Test
  void shouldRejectNullDestinationWhenExtractedConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new Extracted(null));
  }

  @Test
  void shouldRejectNullKindWhenNotDeterminableConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new NotDeterminable(null));
  }

  @Test
  void shouldRejectBlankKindWhenNotDeterminableConstructed() {
    // given / when / then
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> new NotDeterminable(" "));
    assertEquals("rawValueKind must not be blank", error.getMessage());
  }
}
