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

import java.time.Duration;
import java.util.Objects;

/**
 * Thresholds of the analysis, validated once at construction rather than on every invocation.
 *
 * @param window how far back the analysis looks
 * @param repeatThreshold identical calls that trigger the repetition heuristic; at least 2, since a
 *     repetition needs two calls to exist
 * @param novelToolThreshold never-before-seen tools that trigger the burst heuristic
 * @param baselineMinInvocations previous invocations required before the burst heuristic speaks at
 *     all, so a cold start does not report every tool as new
 */
public record AnomalyPolicy(
    Duration window, int repeatThreshold, int novelToolThreshold, long baselineMinInvocations) {

  public AnomalyPolicy {
    Objects.requireNonNull(window, "window");
    if (window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("window must be positive, was " + window);
    }
    if (repeatThreshold < 2) {
      throw new IllegalArgumentException(
          "repeatThreshold must be at least 2, was " + repeatThreshold);
    }
    if (novelToolThreshold < 1) {
      throw new IllegalArgumentException(
          "novelToolThreshold must be at least 1, was " + novelToolThreshold);
    }
    if (baselineMinInvocations < 0) {
      throw new IllegalArgumentException(
          "baselineMinInvocations must not be negative, was " + baselineMinInvocations);
    }
  }
}
