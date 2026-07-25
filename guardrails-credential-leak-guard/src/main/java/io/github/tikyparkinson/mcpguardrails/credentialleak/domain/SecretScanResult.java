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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Outcome of scanning a set of values against the active patterns. */
public record SecretScanResult(List<SecretFinding> findings) {

  public SecretScanResult {
    findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
  }

  /** True when nothing matched. */
  public boolean clean() {
    return findings.isEmpty();
  }

  /** Highest severity found, empty when clean. {@code CONFIRMED} outranks {@code SUSPECTED}. */
  public Optional<SecretSeverity> highestSeverity() {
    return findings.stream().map(SecretFinding::severity).max(SecretSeverity::compareTo);
  }
}
