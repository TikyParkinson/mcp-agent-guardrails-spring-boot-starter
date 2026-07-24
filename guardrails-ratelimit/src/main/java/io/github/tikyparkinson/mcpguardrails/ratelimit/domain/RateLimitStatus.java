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

import java.util.Objects;

/**
 * Result of a rate limit check: the window count after registering the current invocation, against
 * the policy in force.
 */
public record RateLimitStatus(long count, RateLimitPolicy policy) {

  public RateLimitStatus {
    Objects.requireNonNull(policy, "policy");
    if (count < 1) {
      throw new IllegalArgumentException("count must be >= 1, got " + count);
    }
  }

  /** True when the invocation is within the limit. */
  public boolean allowed() {
    return !policy.exceededBy(count);
  }
}
