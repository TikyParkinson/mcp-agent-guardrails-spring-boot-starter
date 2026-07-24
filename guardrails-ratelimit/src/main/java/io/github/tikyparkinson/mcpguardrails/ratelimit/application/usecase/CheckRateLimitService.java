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
package io.github.tikyparkinson.mcpguardrails.ratelimit.application.usecase;

import io.github.tikyparkinson.mcpguardrails.ratelimit.application.port.in.CheckRateLimitUseCase;
import io.github.tikyparkinson.mcpguardrails.ratelimit.application.port.out.RateLimitStorePort;
import io.github.tikyparkinson.mcpguardrails.ratelimit.domain.RateLimitPolicy;
import io.github.tikyparkinson.mcpguardrails.ratelimit.domain.RateLimitStatus;
import java.time.Instant;
import java.util.Objects;

/**
 * Registers each invocation in its fixed window and reports the resulting status. Denied attempts
 * also consume quota (the counter always increments), and store failures propagate (fail-closed).
 */
public final class CheckRateLimitService implements CheckRateLimitUseCase {

  private final RateLimitStorePort store;
  private final RateLimitPolicy policy;

  public CheckRateLimitService(RateLimitStorePort store, RateLimitPolicy policy) {
    this.store = Objects.requireNonNull(store, "store");
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  @Override
  public RateLimitStatus check(String agentId, String toolName, Instant occurredAt) {
    requireNotBlank(agentId, "agentId");
    requireNotBlank(toolName, "toolName");
    Objects.requireNonNull(occurredAt, "occurredAt");
    Instant windowStart = policy.windowStartFor(occurredAt);
    long count = store.incrementAndCount(agentId, toolName, windowStart);
    return new RateLimitStatus(count, policy);
  }

  private static void requireNotBlank(String value, String field) {
    Objects.requireNonNull(value, field);
    if (value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
