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
package io.github.tikyparkinson.mcpguardrails.anomaly.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class ArgumentsFingerprintTest {

  @Test
  void shouldProduceTheSameFingerprintForEquivalentArguments() {
    // given the same entries inserted in opposite order
    Map<String, Object> first = new LinkedHashMap<>();
    first.put("a", 1);
    first.put("b", 2);
    Map<String, Object> second = new LinkedHashMap<>();
    second.put("b", 2);
    second.put("a", 1);

    // when fingerprinted
    // then the two calls are recognised as identical
    assertEquals(ArgumentsFingerprint.of(first), ArgumentsFingerprint.of(second));
  }

  @Test
  void shouldProduceDifferentFingerprintsForDifferentArguments() {
    // given two different calls
    // when fingerprinted
    // then they are not treated as a repetition of one another
    assertNotEquals(
        ArgumentsFingerprint.of(Map.of("q", "one")), ArgumentsFingerprint.of(Map.of("q", "two")));
  }

  @Test
  void shouldNotExposeTheArgumentValues() {
    // given arguments carrying a secret
    Map<String, Object> arguments = Map.of("password", "hunter2", "token", "sk-live-1234");

    // when fingerprinted
    String value = ArgumentsFingerprint.of(arguments).value();

    // then nothing of the input survives: this guardrail stores one record per call and must not
    // become the leak that credential-leak-guard exists to prevent
    assertFalse(value.contains("hunter2") || value.contains("sk-live-1234"), value);
  }

  @Test
  void shouldProduceAHexadecimalDigestOfFixedLength() {
    // given any arguments
    // when fingerprinted
    String value = ArgumentsFingerprint.of(Map.of("a", 1)).value();

    // then the result is a SHA-256 in hex, which can never collide with the UNKNOWN marker
    assertTrue(value.matches("[0-9a-f]{64}"), value);
  }

  @Test
  void shouldReportUnknownAsCarryingNoInformation() {
    // given the marker for a source that cannot supply a fingerprint
    // when asked whether it is known
    // then it is not, so the repetition heuristic can skip it
    assertFalse(ArgumentsFingerprint.unknown().isKnown());
  }

  @Test
  void shouldReportARealFingerprintAsKnown() {
    // given a computed fingerprint
    // when asked whether it is known
    // then it is
    assertTrue(ArgumentsFingerprint.of(Map.of()).isKnown());
  }

  @Test
  void shouldRejectABlankValue() {
    // given a blank fingerprint
    // when constructed
    // then it fails: a blank value would compare equal across unrelated calls
    assertThrows(IllegalArgumentException.class, () -> new ArgumentsFingerprint("  "));
  }

  @Test
  void shouldRejectANullValue() {
    // given no value
    // when constructed
    // then it fails
    assertThrows(NullPointerException.class, () -> new ArgumentsFingerprint(null));
  }

  @Test
  void shouldFailWhenTheJvmCannotProvideTheDigest() {
    // given a runtime that does not offer SHA-256, unreachable on a compliant JVM but the only
    // way this record could ever fail to produce a fingerprint
    try (MockedStatic<MessageDigest> digests = Mockito.mockStatic(MessageDigest.class)) {
      digests
          .when(() -> MessageDigest.getInstance("SHA-256"))
          .thenThrow(new NoSuchAlgorithmException("broken JVM"));

      // when fingerprinted
      // then it fails loudly instead of falling back to a weaker or empty fingerprint, which
      // would silently make every call look identical
      assertThrows(IllegalStateException.class, () -> ArgumentsFingerprint.of(Map.of("a", 1)));
    }
  }

  @Test
  void shouldRejectNullArguments() {
    // given no arguments
    // when fingerprinted
    // then it fails rather than silently fingerprinting nothing
    assertThrows(NullPointerException.class, () -> ArgumentsFingerprint.of(null));
  }
}
