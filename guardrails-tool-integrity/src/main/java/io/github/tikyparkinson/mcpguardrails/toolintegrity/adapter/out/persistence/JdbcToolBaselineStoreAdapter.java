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
package io.github.tikyparkinson.mcpguardrails.toolintegrity.adapter.out.persistence;

import io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port.out.ToolBaselineStorePort;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.ToolFingerprint;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reference JDBC adapter for the baseline store, tested against PostgreSQL with Testcontainers.
 * Persistence is the point: TOFU baselines survive restarts, which is where rug-pull attacks live.
 * Expects the {@code mcp_tool_baseline} table from {@code
 * mcp-guardrails-tool-integrity-schema.sql}.
 */
public final class JdbcToolBaselineStoreAdapter implements ToolBaselineStorePort {

  private static final String TOOL_NAME = "toolName";

  private static final String FINGERPRINT = "fingerprint";

  private static final String SELECT_SQL =
      "SELECT fingerprint FROM mcp_tool_baseline WHERE tool_name = :toolName";

  private static final String INSERT_IF_ABSENT_SQL =
      "INSERT INTO mcp_tool_baseline (tool_name, fingerprint) VALUES (:toolName, :fingerprint) "
          + "ON CONFLICT (tool_name) DO NOTHING";

  private static final String UPSERT_SQL =
      "INSERT INTO mcp_tool_baseline (tool_name, fingerprint) VALUES (:toolName, :fingerprint) "
          + "ON CONFLICT (tool_name) DO UPDATE SET fingerprint = EXCLUDED.fingerprint, "
          + "established_at = now()";

  private final JdbcClient jdbcClient;

  public JdbcToolBaselineStoreAdapter(JdbcClient jdbcClient) {
    this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
  }

  @Override
  public Optional<ToolFingerprint> find(String toolName) {
    Objects.requireNonNull(toolName, TOOL_NAME);
    return jdbcClient
        .sql(SELECT_SQL)
        .param(TOOL_NAME, toolName)
        .query(String.class)
        .optional()
        .map(String::strip)
        .map(ToolFingerprint::new);
  }

  @Override
  public ToolFingerprint establishIfAbsent(String toolName, ToolFingerprint candidate) {
    Objects.requireNonNull(toolName, TOOL_NAME);
    Objects.requireNonNull(candidate, "candidate");
    jdbcClient
        .sql(INSERT_IF_ABSENT_SQL)
        .param(TOOL_NAME, toolName)
        .param(FINGERPRINT, candidate.value())
        .update();
    return find(toolName)
        .orElseThrow(() -> new IllegalStateException("baseline vanished for " + toolName));
  }

  @Override
  public void replace(String toolName, ToolFingerprint fingerprint) {
    Objects.requireNonNull(toolName, TOOL_NAME);
    Objects.requireNonNull(fingerprint, FINGERPRINT);
    jdbcClient
        .sql(UPSERT_SQL)
        .param(TOOL_NAME, toolName)
        .param(FINGERPRINT, fingerprint.value())
        .update();
  }
}
