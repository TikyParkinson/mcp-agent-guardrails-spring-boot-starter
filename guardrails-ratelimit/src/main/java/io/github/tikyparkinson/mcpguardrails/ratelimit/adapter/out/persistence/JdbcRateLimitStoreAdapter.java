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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reference JDBC adapter for the rate limit counter, tested against PostgreSQL with Testcontainers.
 * Uses a single atomic upsert ({@code ON CONFLICT ... RETURNING}) so concurrent invocations never
 * lose increments. Expects the {@code mcp_rate_limit_counter} table from {@code
 * mcp-guardrails-ratelimit-schema.sql}.
 */
public final class JdbcRateLimitStoreAdapter implements RateLimitStorePort {

  private static final String UPSERT_SQL =
      "INSERT INTO mcp_rate_limit_counter (agent_id, tool_name, window_start, invocation_count) "
          + "VALUES (:agentId, :toolName, :windowStart, 1) "
          + "ON CONFLICT (agent_id, tool_name, window_start) "
          + "DO UPDATE SET invocation_count = mcp_rate_limit_counter.invocation_count + 1 "
          + "RETURNING invocation_count";

  private final JdbcClient jdbcClient;

  public JdbcRateLimitStoreAdapter(JdbcClient jdbcClient) {
    this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
  }

  @Override
  public long incrementAndCount(String agentId, String toolName, Instant windowStart) {
    Objects.requireNonNull(agentId, "agentId");
    Objects.requireNonNull(toolName, "toolName");
    Objects.requireNonNull(windowStart, "windowStart");
    Long count =
        jdbcClient
            .sql(UPSERT_SQL)
            .param("agentId", agentId)
            .param("toolName", toolName)
            .param("windowStart", Timestamp.from(windowStart))
            .query(Long.class)
            .single();
    if (count == null) {
      throw new IllegalStateException(
          "rate limit upsert returned no counter for tool '" + toolName + "'");
    }
    return count;
  }
}
