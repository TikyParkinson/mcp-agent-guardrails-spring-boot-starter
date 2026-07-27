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

/**
 * Outcome of scanning a set of values against the active patterns.
 *
 * @param findings what matched and where
 * @param complete false when the walk ran out of budget, so part of the arguments was never seen.
 *     Nothing matching is not the same as nothing being there, and the guardrail treats the two
 *     differently
 */
public record SecretScanResult(List<SecretFinding> findings, boolean complete) {

  public SecretScanResult {
    findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
  }

  /** A result from a walk that finished. */
  public SecretScanResult(List<SecretFinding> findings) {
    this(findings, true);
  }

  /** True when nothing matched. Says nothing about whether everything was looked at. */
  public boolean clean() {
    return findings.isEmpty();
  }

  /** Highest severity found, empty when clean. {@code CONFIRMED} outranks {@code SUSPECTED}. */
  public Optional<SecretSeverity> highestSeverity() {
    return findings.stream().map(SecretFinding::severity).max(SecretSeverity::compareTo);
  }
}
