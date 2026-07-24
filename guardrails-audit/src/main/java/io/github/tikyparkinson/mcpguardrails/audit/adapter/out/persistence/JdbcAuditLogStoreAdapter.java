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
package io.github.tikyparkinson.mcpguardrails.audit.adapter.out.persistence;

import io.github.tikyparkinson.mcpguardrails.audit.application.port.out.AuditLogStorePort;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEvent;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEventType;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reference JDBC adapter for the audit log, tested against PostgreSQL with Testcontainers. Expects
 * the {@code mcp_audit_log} table from {@code mcp-guardrails-audit-schema.sql}. Use it as-is or
 * replace it by exposing your own {@link AuditLogStorePort} bean.
 */
public final class JdbcAuditLogStoreAdapter implements AuditLogStorePort {

  private static final String INSERT_SQL =
      "INSERT INTO mcp_audit_log "
          + "(event_id, agent_id, tool_name, occurred_at, emitted_by, event_type, detail) "
          + "VALUES (:eventId, :agentId, :toolName, :occurredAt, :emittedBy, :eventType, :detail)";

  private static final String SELECT_RECENT_SQL =
      "SELECT event_id, agent_id, tool_name, occurred_at, emitted_by, event_type, detail "
          + "FROM mcp_audit_log ORDER BY occurred_at DESC, event_id DESC LIMIT :limit";

  private final JdbcClient jdbcClient;

  public JdbcAuditLogStoreAdapter(JdbcClient jdbcClient) {
    this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
  }

  @Override
  public void append(AuditEvent event) {
    Objects.requireNonNull(event, "event");
    jdbcClient
        .sql(INSERT_SQL)
        .param("eventId", event.eventId())
        .param("agentId", event.agentId())
        .param("toolName", event.toolName())
        .param("occurredAt", Timestamp.from(event.occurredAt()))
        .param("emittedBy", event.emittedBy())
        .param("eventType", event.type().name())
        .param("detail", event.detail())
        .update();
  }

  @Override
  public List<AuditEvent> findRecent(int limit) {
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be >= 1, got " + limit);
    }
    return jdbcClient
        .sql(SELECT_RECENT_SQL)
        .param("limit", limit)
        .query(
            (rs, rowNum) ->
                new AuditEvent(
                    rs.getObject("event_id", UUID.class),
                    rs.getString("agent_id"),
                    rs.getString("tool_name"),
                    // PG driver maps timestamptz to OffsetDateTime, not Instant
                    rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                    rs.getString("emitted_by"),
                    AuditEventType.valueOf(rs.getString("event_type")),
                    rs.getString("detail")))
        .list();
  }
}
