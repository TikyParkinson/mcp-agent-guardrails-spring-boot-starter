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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Recovers text hidden inside a Base64 payload so the patterns can see it.
 *
 * <p>This covers an accident rather than an attack. A {@code .env} file or a configuration blob
 * serialized into a JSON argument is Base64 without anyone trying to fool anyone, and today the
 * whole thing sails past every pattern. Splitting a key in half or reversing it also preserves the
 * secret, but neither happens by accident, and searching for permutations of the text would make
 * the guardrail unusable through false positives.
 *
 * <p>Against an agent that wants to exfiltrate on purpose, none of this helps — encrypting the
 * value defeats any pattern-based detector. That boundary belongs in the README, not in more code.
 *
 * <p>Only one level is decoded. Base64 inside Base64 is deliberate obfuscation, and unbounded
 * recursive decoding is a decompression bomb waiting for someone.
 */
public final class Base64Decoder {

  /**
   * Values worth attempting. Shorter runs are ordinary words and identifiers, and requiring a
   * minimum keeps the common path from allocating at all.
   */
  private static final Pattern LOOKS_LIKE_BASE64 =
      Pattern.compile("^[A-Za-z0-9+/\\r\\n]{16,}={0,2}$");

  /**
   * Ceiling on what is decoded. A decoded payload is held in memory in full, so an unbounded limit
   * would let one oversized argument cost more than the invocation it protects.
   */
  private static final int MAX_ENCODED_LENGTH = 64 * 1024;

  private Base64Decoder() {}

  /**
   * Returns the decoded text, or empty when the value is not Base64 worth scanning.
   *
   * <p>Never throws for malformed input: a value that almost looks like Base64 is the normal case,
   * not an exceptional one.
   */
  public static Optional<String> decode(String value) {
    Objects.requireNonNull(value, "value");
    String candidate = value.strip();
    if (candidate.length() > MAX_ENCODED_LENGTH
        || !LOOKS_LIKE_BASE64.matcher(candidate).matches()) {
      return Optional.empty();
    }
    try {
      String decoded =
          new String(Base64.getMimeDecoder().decode(candidate), StandardCharsets.UTF_8);
      return isPrintable(decoded) ? Optional.of(decoded) : Optional.empty();
    } catch (IllegalArgumentException notBase64) {
      return Optional.empty();
    }
  }

  /**
   * Arbitrary bytes decode into binary noise that cannot hold a secret in text form. Passing that
   * to the patterns would only add findings nobody can act on.
   */
  private static boolean isPrintable(String decoded) {
    if (decoded.isEmpty()) {
      return false;
    }
    return decoded.chars().noneMatch(Base64Decoder::isControlOther);
  }

  private static boolean isControlOther(int codePoint) {
    return Character.getType(codePoint) == Character.CONTROL
        && codePoint != '\n'
        && codePoint != '\r'
        && codePoint != '\t';
  }
}
