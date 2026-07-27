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

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Detection pattern.
 *
 * <p>{@code secretGroup} tells which capture group holds the sensitive value: {@code 0} is the
 * whole match (bare token patterns), {@code n > 0} is that group alone, used when the regex also
 * has to match the key in front of the value ({@code password=}, {@code Bearer }). Redacting the
 * whole match in those cases would destroy the surrounding text.
 */
public record SecretPattern(String id, Pattern pattern, SecretSeverity severity, int secretGroup) {

  public SecretPattern {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(pattern, "pattern");
    Objects.requireNonNull(severity, "severity");
    if (id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
    if (secretGroup < 0) {
      throw new IllegalArgumentException("secretGroup must not be negative, was " + secretGroup);
    }
    int groups = pattern.matcher("").groupCount();
    if (secretGroup > groups) {
      throw new IllegalArgumentException(
          "secretGroup %d exceeds the %d capture groups of pattern %s"
              .formatted(secretGroup, groups, id));
    }
  }

  /** Compiles a pattern whose whole match is the sensitive value. */
  public static SecretPattern of(String id, String regex, SecretSeverity severity) {
    return of(id, regex, severity, 0);
  }

  /** Compiles a pattern, keeping only {@code secretGroup} as the sensitive value. */
  public static SecretPattern of(
      String id, String regex, SecretSeverity severity, int secretGroup) {
    Objects.requireNonNull(regex, "regex");
    return new SecretPattern(id, compile(id, regex), severity, secretGroup);
  }

  /**
   * Builds a pattern that matches one known secret value literally, for sets fed from a secret
   * manager (Vault, CyberArk, Azure Key Vault).
   *
   * <p>The value is quoted, so regex metacharacters in it are matched as themselves instead of
   * widening the pattern or failing to compile.
   */
  public static SecretPattern ofLiteral(String id, String value, SecretSeverity severity) {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank for pattern " + id);
    }
    return new SecretPattern(id, Pattern.compile(Pattern.quote(value)), severity, 0);
  }

  private static Pattern compile(String id, String regex) {
    try {
      return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException("invalid regex for pattern " + id, e);
    }
  }
}
