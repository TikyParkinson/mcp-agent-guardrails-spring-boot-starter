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
package io.github.tikyparkinson.mcpguardrails.ratelimit.adapter.out.persistence;

import io.github.tikyparkinson.mcpguardrails.ratelimit.application.port.out.RateLimitStorePort;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default in-memory counter store. Windows older than the one being touched are evicted lazily on
 * increment (no cleanup threads — the library must not manage its own lifecycle). Intended for
 * single-instance deployments; use the JDBC adapter (or your own) for shared state.
 */
public final class InMemoryRateLimitStoreAdapter implements RateLimitStorePort {

  private record WindowKey(String agentId, String toolName, Instant windowStart) {}

  private final Map<WindowKey, AtomicLong> counters = new ConcurrentHashMap<>();

  @Override
  public long incrementAndCount(String agentId, String toolName, Instant windowStart) {
    Objects.requireNonNull(agentId, "agentId");
    Objects.requireNonNull(toolName, "toolName");
    Objects.requireNonNull(windowStart, "windowStart");
    evictOlderWindows(agentId, toolName, windowStart);
    return counters
        .computeIfAbsent(new WindowKey(agentId, toolName, windowStart), key -> new AtomicLong())
        .incrementAndGet();
  }

  private void evictOlderWindows(String agentId, String toolName, Instant current) {
    counters
        .keySet()
        .removeIf(
            key ->
                key.agentId().equals(agentId)
                    && key.toolName().equals(toolName)
                    && key.windowStart().isBefore(current));
  }
}
