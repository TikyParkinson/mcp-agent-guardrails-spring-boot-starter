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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;

/**
 * Replaces every detected secret with {@code [REDACTED:<patternId>]}, keeping no character of the
 * original value.
 *
 * <p>Only the {@code secretGroup} of each pattern is replaced, so the key in front of the value
 * survives: {@code DB_PASSWORD=hunter2000} becomes {@code
 * DB_PASSWORD=[REDACTED:credential-assignment]} instead of {@code DB_[REDACTED:…]}. The text is
 * rewritten pattern by pattern, so later patterns see what the earlier ones already sanitized and
 * cannot match a value that is already gone.
 */
public final class SecretRedactor {

  private SecretRedactor() {}

  /** Redacts the text with the given patterns, reporting every detection at {@code location}. */
  public static RedactedText redact(String text, List<SecretPattern> patterns, String location) {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(patterns, "patterns");
    Objects.requireNonNull(location, "location");
    String current = text;
    List<SecretFinding> findings = new ArrayList<>();
    for (SecretPattern pattern : patterns) {
      RedactedText step = apply(pattern, current, location);
      current = step.sanitizedText();
      findings.addAll(step.findings());
    }
    return new RedactedText(current, findings);
  }

  private static RedactedText apply(SecretPattern pattern, String text, String location) {
    Matcher matcher = pattern.pattern().matcher(text);
    StringBuilder sanitized = new StringBuilder();
    List<SecretFinding> findings = new ArrayList<>();
    int copiedUpTo = 0;
    while (matcher.find()) {
      int group = redactableGroup(matcher, pattern.secretGroup());
      sanitized.append(text, copiedUpTo, matcher.start(group)).append(marker(pattern.id()));
      copiedUpTo = matcher.end(group);
      findings.add(new SecretFinding(pattern.id(), pattern.severity(), location));
    }
    sanitized.append(text, copiedUpTo, text.length());
    return new RedactedText(sanitized.toString(), findings);
  }

  /**
   * Falls back to the whole match when the configured group did not take part in it: a custom
   * pattern with an optional group must never leave the value in place.
   */
  private static int redactableGroup(Matcher matcher, int secretGroup) {
    return matcher.start(secretGroup) < 0 ? 0 : secretGroup;
  }

  private static String marker(String patternId) {
    return "[REDACTED:" + patternId + "]";
  }
}
