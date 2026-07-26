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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/**
 * SHA-256 of the canonical form of a call's arguments, never the arguments themselves: comparing
 * invocations must not reintroduce the leak risk that {@code credential-leak-guard} prevents.
 *
 * <p>{@link #UNKNOWN} marks a history source that cannot supply a fingerprint — the audit log, for
 * one, deliberately stores no arguments. Repetition analysis skips those records instead of
 * treating them as identical to each other.
 */
public record ArgumentsFingerprint(String value) {

  /** Placeholder for records whose arguments are unavailable. */
  public static final ArgumentsFingerprint UNKNOWN = new ArgumentsFingerprint("unknown");

  public ArgumentsFingerprint {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
  }

  /** Fingerprints the given arguments. */
  public static ArgumentsFingerprint of(Map<String, Object> arguments) {
    Objects.requireNonNull(arguments, "arguments");
    byte[] canonical = CanonicalArguments.of(arguments).getBytes(StandardCharsets.UTF_8);
    return new ArgumentsFingerprint(HexFormat.of().formatHex(sha256().digest(canonical)));
  }

  /** The fingerprint used when the source cannot supply one. */
  public static ArgumentsFingerprint unknown() {
    return UNKNOWN;
  }

  /** False when this fingerprint carries no information about the arguments. */
  public boolean isKnown() {
    return !equals(UNKNOWN);
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is mandatory on every compliant JVM", e);
    }
  }
}
