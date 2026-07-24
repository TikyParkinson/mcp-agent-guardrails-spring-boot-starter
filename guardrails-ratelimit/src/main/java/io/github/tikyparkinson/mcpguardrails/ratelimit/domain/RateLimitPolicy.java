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
package io.github.tikyparkinson.mcpguardrails.ratelimit.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Fixed-window rate limit policy: at most {@code maxInvocations} per {@code window} for each
 * (agent, tool) pair.
 */
public record RateLimitPolicy(int maxInvocations, Duration window) {

  public RateLimitPolicy {
    Objects.requireNonNull(window, "window");
    if (maxInvocations < 1) {
      throw new IllegalArgumentException("maxInvocations must be >= 1, got " + maxInvocations);
    }
    if (window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("window must be positive, got " + window);
    }
  }

  /** Start of the fixed window containing the given instant. */
  public Instant windowStartFor(Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt");
    long windowMillis = window.toMillis();
    long epochMillis = occurredAt.toEpochMilli();
    return Instant.ofEpochMilli(epochMillis - Math.floorMod(epochMillis, windowMillis));
  }

  /** True when a count (already including the current invocation) exceeds the limit. */
  public boolean exceededBy(long count) {
    return count > maxInvocations;
  }
}
