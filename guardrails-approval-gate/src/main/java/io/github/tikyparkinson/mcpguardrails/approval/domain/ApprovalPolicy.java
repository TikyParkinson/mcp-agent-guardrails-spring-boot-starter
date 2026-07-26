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
package io.github.tikyparkinson.mcpguardrails.approval.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * Gate settings, validated once at construction rather than on every invocation.
 *
 * @param timeout how long an invocation is held waiting for an answer; must be positive
 * @param maxPending requests admitted at once, protecting the server's thread pool since every wait
 *     holds one; at least 1
 * @param maxPendingPerAgent cap per agent, so one looping agent cannot fill the global quota and
 *     leave everyone else without a channel; at least 1 and no greater than {@code maxPending}
 * @param includeArguments whether the invocation arguments travel in the request
 */
public record ApprovalPolicy(
    Duration timeout, int maxPending, int maxPendingPerAgent, boolean includeArguments) {

  public ApprovalPolicy {
    Objects.requireNonNull(timeout, "timeout");
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive, was " + timeout);
    }
    if (maxPending < 1) {
      throw new IllegalArgumentException("maxPending must be at least 1, was " + maxPending);
    }
    if (maxPendingPerAgent < 1) {
      throw new IllegalArgumentException(
          "maxPendingPerAgent must be at least 1, was " + maxPendingPerAgent);
    }
    if (maxPendingPerAgent > maxPending) {
      throw new IllegalArgumentException(
          "maxPendingPerAgent (%d) must not exceed maxPending (%d)"
              .formatted(maxPendingPerAgent, maxPending));
    }
  }
}
