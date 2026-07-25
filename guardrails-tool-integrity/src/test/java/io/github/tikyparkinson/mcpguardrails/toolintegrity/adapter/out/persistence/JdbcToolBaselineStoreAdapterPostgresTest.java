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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.ToolFingerprint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * (ARCHITECTURE.md §8), including atomic TOFU establishment under concurrency.
 */
@Testcontainers
class JdbcToolBaselineStoreAdapterPostgresTest {

  @Container
  private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  private static final ToolFingerprint FP_A = new ToolFingerprint("a".repeat(64));
  private static final ToolFingerprint FP_B = new ToolFingerprint("b".repeat(64));

  private static JdbcClient jdbcClient;
  private JdbcToolBaselineStoreAdapter adapter;

  @BeforeAll
  static void createSchema() throws Exception {
    PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setUrl(postgres.getJdbcUrl());
    dataSource.setUser(postgres.getUsername());
    dataSource.setPassword(postgres.getPassword());
    jdbcClient = JdbcClient.create((DataSource) dataSource);
    String ddl =
        Files.readString(Path.of("src/main/resources/mcp-guardrails-tool-integrity-schema.sql"));
    jdbcClient.sql(ddl).update();
  }

  @BeforeEach
  void cleanTable() {
    jdbcClient.sql("DELETE FROM mcp_tool_baseline").update();
    adapter = new JdbcToolBaselineStoreAdapter(jdbcClient);
  }

  @Test
  void shouldRoundTripBaselineWhenEstablished() {
    // given / when
    ToolFingerprint winner = adapter.establishIfAbsent("search", FP_A);

    // then: full fidelity through the real database (CHAR(64) padding included)
    assertEquals(FP_A, winner);
    assertEquals(Optional.of(FP_A), adapter.find("search"));
  }

  @Test
  void shouldKeepFirstBaselineWhenEstablishingTwice() {
    // given: TOFU against the real ON CONFLICT path
    adapter.establishIfAbsent("search", FP_A);

    // when
    ToolFingerprint winner = adapter.establishIfAbsent("search", FP_B);

    // then
    assertEquals(FP_A, winner);
  }

  @Test
  void shouldReturnEmptyWhenToolHasNoBaseline() {
    // given / when / then
    assertEquals(Optional.empty(), adapter.find("unknown"));
  }

  @Test
  void shouldOverwriteBaselineWhenReplaced() {
    // given
    adapter.establishIfAbsent("search", FP_A);

    // when: approval flow
    adapter.replace("search", FP_B);

    // then
    assertEquals(Optional.of(FP_B), adapter.find("search"));

    // and replace also creates when absent (upsert)
    adapter.replace("brand-new", FP_A);
    assertEquals(Optional.of(FP_A), adapter.find("brand-new"));
  }

  @Test
  void shouldElectExactlyOneWinnerWhenEstablishingConcurrently() throws Exception {
    // given: 8 threads race with distinct fingerprints for the same tool
    int threads = 8;
    List<ToolFingerprint> candidates = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      candidates.add(new ToolFingerprint(Integer.toHexString(i).repeat(64).substring(0, 64)));
    }
    Set<ToolFingerprint> winners = new HashSet<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      List<Callable<ToolFingerprint>> tasks = new ArrayList<>();
      for (ToolFingerprint candidate : candidates) {
        tasks.add(() -> adapter.establishIfAbsent("search", candidate));
      }

      // when
      for (Future<ToolFingerprint> future : pool.invokeAll(tasks)) {
        winners.add(future.get());
      }
    }

    // then: ON CONFLICT guarantees a single winner observed by every thread
    assertEquals(1, winners.size());
    assertTrue(candidates.contains(winners.iterator().next()));
  }
}
