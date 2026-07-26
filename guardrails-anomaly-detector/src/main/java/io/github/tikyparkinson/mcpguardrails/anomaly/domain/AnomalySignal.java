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

import java.util.Locale;
import java.util.Objects;

/**
 * A detected signal, carrying the numbers that justify it so an operator can check the call by hand
 * instead of trusting a verdict.
 *
 * @param observed measured value: repetitions, or number of novel tools
 * @param threshold configured threshold that was reached
 * @param subject what repeated (a tool name), or the novel tools joined by {@code ", "}
 */
public record AnomalySignal(AnomalyKind kind, int observed, int threshold, String subject) {

  public AnomalySignal {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(subject, "subject");
    if (subject.isBlank()) {
      throw new IllegalArgumentException("subject must not be blank");
    }
    if (threshold < 1) {
      throw new IllegalArgumentException("threshold must be at least 1, was " + threshold);
    }
    if (observed < threshold) {
      throw new IllegalArgumentException(
          "observed %d did not reach threshold %d".formatted(observed, threshold));
    }
  }

  /** Human-readable form used in the escalation reason. */
  public String describe() {
    String name = kind.name().toLowerCase(Locale.ROOT).replace('_', '-');
    return switch (kind) {
      case REPETITION_LOOP ->
          "%s: %d identical calls to '%s' (threshold %d)"
              .formatted(name, observed, subject, threshold);
      case NOVEL_TOOL_BURST ->
          "%s: %d tools never used before (%s), threshold %d"
              .formatted(name, observed, subject, threshold);
    };
  }
}
