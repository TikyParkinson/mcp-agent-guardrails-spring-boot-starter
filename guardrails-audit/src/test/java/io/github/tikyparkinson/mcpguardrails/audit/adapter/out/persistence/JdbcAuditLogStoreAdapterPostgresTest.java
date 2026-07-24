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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEvent;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEventType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for the reference JDBC adapter against a real PostgreSQL container
 * (ARCHITECTURE.md §8: Testcontainers is mandatory for real-store out-adapters).
 */
@Testcontainers
class JdbcAuditLogStoreAdapterPostgresTest {

  @Container
  private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  private static JdbcClient jdbcClient;
  private JdbcAuditLogStoreAdapter adapter;

  @BeforeAll
  static void createSchema() throws Exception {
    PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setUrl(postgres.getJdbcUrl());
    dataSource.setUser(postgres.getUsername());
    dataSource.setPassword(postgres.getPassword());
    jdbcClient = JdbcClient.create((DataSource) dataSource);
    String ddl = Files.readString(Path.of("src/main/resources/mcp-guardrails-audit-schema.sql"));
    jdbcClient.sql(ddl).update();
  }

  @BeforeEach
  void cleanTable() {
    jdbcClient.sql("DELETE FROM mcp_audit_log").update();
    adapter = new JdbcAuditLogStoreAdapter(jdbcClient);
  }

  @Test
  void shouldRoundTripEventWhenAppendedToPostgres() {
    // given
    AuditEvent event = event(1, AuditEventType.TOOL_INVOKED);

    // when
    adapter.append(event);

    // then: full fidelity round-trip through the real database
    assertEquals(List.of(event), adapter.findRecent(10));
  }

  @Test
  void shouldReturnMostRecentFirstAndHonorLimitWhenFindingRecent() {
    // given
    adapter.append(event(1, AuditEventType.TOOL_INVOKED));
    adapter.append(event(2, AuditEventType.DECISION_DENY));
    adapter.append(event(3, AuditEventType.DECISION_ESCALATE));

    // when
    List<AuditEvent> recent = adapter.findRecent(2);

    // then
    assertEquals(
        List.of(event(3, AuditEventType.DECISION_ESCALATE), event(2, AuditEventType.DECISION_DENY)),
        recent);
  }

  @Test
  void shouldReturnEmptyListWhenTableIsEmpty() {
    // given / when / then
    assertEquals(List.of(), adapter.findRecent(5));
  }

  @Test
  void shouldRejectDuplicateEventIdWhenAppending() {
    // given: primary key enforced by the real database, not a mock
    AuditEvent event = event(1, AuditEventType.TOOL_INVOKED);
    adapter.append(event);

    // when / then
    assertThrows(RuntimeException.class, () -> adapter.append(event));
  }

  @Test
  void shouldRejectInvalidLimitWhenFindingRecent() {
    // given / when / then
    assertThrows(IllegalArgumentException.class, () -> adapter.findRecent(0));
  }

  private static AuditEvent event(int seq, AuditEventType type) {
    return new AuditEvent(
        new UUID(0, seq),
        "agent-1",
        "search",
        Instant.parse("2026-07-24T10:00:00Z").plusSeconds(seq),
        "audit",
        type,
        "detail-" + seq);
  }
}
