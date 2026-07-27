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

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Outcome of scanning one invocation's arguments against the active rules. */
public record ScanResult(List<Finding> findings, boolean complete) {

  public ScanResult {
    findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
  }

  /** A result from a walk that finished. */
  public ScanResult(List<Finding> findings) {
    this(findings, true);
  }

  /** True when no rule fired. Says nothing about whether everything was looked at. */
  public boolean clean() {
    return findings.isEmpty();
  }

  /** Highest severity among the findings; empty when clean. MALICIOUS &gt; SUSPICIOUS. */
  public Optional<InjectionSeverity> highestSeverity() {
    return findings.stream().map(Finding::severity).max(Comparator.naturalOrder());
  }

  /** One rule firing on one argument value. */
  public record Finding(String ruleId, InjectionSeverity severity, String argumentPath) {

    public Finding {
      Objects.requireNonNull(severity, "severity");
      Objects.requireNonNull(argumentPath, "argumentPath");
      Objects.requireNonNull(ruleId, "ruleId");
      if (ruleId.isBlank()) {
        throw new IllegalArgumentException("ruleId must not be blank");
      }
    }
  }
}
