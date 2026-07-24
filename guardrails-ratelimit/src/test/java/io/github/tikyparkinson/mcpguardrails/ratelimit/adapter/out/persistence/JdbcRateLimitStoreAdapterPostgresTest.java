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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
 * (ARCHITECTURE.md §8), including upsert atomicity under concurrency.
 */
@Testcontainers
class JdbcRateLimitStoreAdapterPostgresTest {

  @Container
  private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  private static final Instant WINDOW_A = Instant.parse("2026-07-24T10:05:00Z");
  private static final Instant WINDOW_B = Instant.parse("2026-07-24T10:06:00Z");

  private static JdbcClient jdbcClient;
  private JdbcRateLimitStoreAdapter adapter;

  @BeforeAll
  static void createSchema() throws Exception {
    PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setUrl(postgres.getJdbcUrl());
    dataSource.setUser(postgres.getUsername());
    dataSource.setPassword(postgres.getPassword());
    jdbcClient = JdbcClient.create((DataSource) dataSource);
    String ddl =
        Files.readString(Path.of("src/main/resources/mcp-guardrails-ratelimit-schema.sql"));
    jdbcClient.sql(ddl).update();
  }

  @BeforeEach
  void cleanTable() {
    jdbcClient.sql("DELETE FROM mcp_rate_limit_counter").update();
    adapter = new JdbcRateLimitStoreAdapter(jdbcClient);
  }

  @Test
  void shouldReturnSequentialCountsWhenIncrementingSameKey() {
    // given / when / then: upsert path covers both INSERT and ON CONFLICT UPDATE
    assertEquals(1, adapter.incrementAndCount("agent-1", "search", WINDOW_A));
    assertEquals(2, adapter.incrementAndCount("agent-1", "search", WINDOW_A));
    assertEquals(3, adapter.incrementAndCount("agent-1", "search", WINDOW_A));
  }

  @Test
  void shouldCountIndependentlyWhenWindowOrKeyDiffers() {
    // given
    adapter.incrementAndCount("agent-1", "search", WINDOW_A);

    // when / then
    assertEquals(1, adapter.incrementAndCount("agent-1", "search", WINDOW_B));
    assertEquals(1, adapter.incrementAndCount("agent-2", "search", WINDOW_A));
    assertEquals(1, adapter.incrementAndCount("agent-1", "delete", WINDOW_A));
  }

  @Test
  void shouldNotLoseIncrementsWhenCalledConcurrently() throws Exception {
    // given: 8 threads × 50 increments against the same key
    int threads = 8;
    int perThread = 50;
    Set<Long> seen = new HashSet<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      List<Callable<List<Long>>> tasks = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        tasks.add(
            () -> {
              List<Long> counts = new ArrayList<>();
              for (int i = 0; i < perThread; i++) {
                counts.add(adapter.incrementAndCount("agent-1", "search", WINDOW_A));
              }
              return counts;
            });
      }

      // when
      for (Future<List<Long>> future : pool.invokeAll(tasks)) {
        seen.addAll(future.get());
      }
    }

    // then: every count 1..400 was returned exactly once — atomic, no lost updates
    assertEquals(threads * perThread, seen.size());
    assertTrue(seen.contains(1L));
    assertTrue(seen.contains((long) threads * perThread));
  }
}
