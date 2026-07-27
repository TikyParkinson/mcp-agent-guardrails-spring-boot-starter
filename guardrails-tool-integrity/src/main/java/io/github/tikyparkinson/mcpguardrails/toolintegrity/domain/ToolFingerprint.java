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
package io.github.tikyparkinson.mcpguardrails.toolintegrity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** SHA-256 fingerprint of a tool definition's canonical form. */
public record ToolFingerprint(String value) {

  private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

  public ToolFingerprint {
    Objects.requireNonNull(value, "value");
    if (!SHA256_HEX.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "fingerprint must be 64 lowercase hex characters, got: " + value);
    }
  }

  /** Computes the fingerprint of the given definition. */
  public static ToolFingerprint of(ToolDefinition definition) {
    Objects.requireNonNull(definition, "definition");
    byte[] canonical = CanonicalForm.render(definition).getBytes(StandardCharsets.UTF_8);
    return new ToolFingerprint(HexFormat.of().formatHex(sha256().digest(canonical)));
  }

  /** First 12 hex characters, for human-readable messages. */
  public String shortForm() {
    return value.substring(0, 12);
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is mandatory on every JVM; unreachable in practice
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
