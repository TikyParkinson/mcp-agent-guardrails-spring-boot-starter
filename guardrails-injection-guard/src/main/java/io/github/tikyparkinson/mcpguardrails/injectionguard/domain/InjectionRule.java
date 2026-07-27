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
package io.github.tikyparkinson.mcpguardrails.injectionguard.domain;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** One detection rule: a precompiled, case-insensitive regex with a stable id and severity. */
public record InjectionRule(String id, Pattern pattern, InjectionSeverity severity) {

  public InjectionRule {
    Objects.requireNonNull(pattern, "pattern");
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(id, "id");
    if (id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
  }

  /**
   * Compiles the regex case-insensitively, across lines, and with Unicode-aware case folding.
   * Without {@code UNICODE_CASE} the insensitivity is ASCII-only, so a custom rule written in a
   * non-Latin script would not match its own upper-case form. Invalid regex ⇒
   * IllegalArgumentException.
   */
  public static InjectionRule of(String id, String regex, InjectionSeverity severity) {
    Objects.requireNonNull(regex, "regex");
    try {
      Pattern compiled =
          Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
      return new InjectionRule(id, compiled, severity);
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException("Invalid regex for rule '" + id + "': " + regex, e);
    }
  }

  /** True when the rule fires on the given text. */
  public boolean matches(String text) {
    return pattern.matcher(text).find();
  }
}
