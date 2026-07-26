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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The paths that a real PostgreSQL never takes, and that {@code
 * JdbcRateLimitStoreAdapterPostgresTest} therefore cannot reach.
 */
class JdbcRateLimitStoreAdapterTest {

  private static final Instant WINDOW_START = Instant.parse("2026-07-26T10:00:00Z");

  @Test
  void shouldFailWhenTheUpsertReturnsNoCounter() {
    // given a store that answers with no counter, which a broken schema or a driver returning a
    // null column would produce
    JdbcClient jdbcClient = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
    when(jdbcClient
            .sql(anyString())
            .param(anyString(), any())
            .param(anyString(), any())
            .param(anyString(), any())
            .query(Long.class)
            .single())
        .thenReturn(null);
    JdbcRateLimitStoreAdapter adapter = new JdbcRateLimitStoreAdapter(jdbcClient);

    // when the counter is incremented
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () -> adapter.incrementAndCount("agent-1", "search", WINDOW_START));

    // then it fails with a message naming the tool, rather than an unboxing NullPointerException
    // that says nothing. The chain turns either into a Deny, so the guardrail stays fail-closed
    // both ways, but only one of them tells the operator what broke
    assertTrue(failure.getMessage().contains("search"), failure.getMessage());
  }

  @Test
  void shouldRejectAnInvocationWithoutAnAgent() {
    // given a store
    JdbcRateLimitStoreAdapter adapter =
        new JdbcRateLimitStoreAdapter(mock(JdbcClient.class, RETURNS_DEEP_STUBS));

    // when the agent is missing
    // then it fails before touching the database
    assertThrows(
        NullPointerException.class, () -> adapter.incrementAndCount(null, "search", WINDOW_START));
  }

  @Test
  void shouldRejectAnInvocationWithoutATool() {
    // given a store
    JdbcRateLimitStoreAdapter adapter =
        new JdbcRateLimitStoreAdapter(mock(JdbcClient.class, RETURNS_DEEP_STUBS));

    // when the tool is missing
    // then it fails
    assertThrows(
        NullPointerException.class, () -> adapter.incrementAndCount("agent-1", null, WINDOW_START));
  }

  @Test
  void shouldRejectAnInvocationWithoutAWindow() {
    // given a store
    JdbcRateLimitStoreAdapter adapter =
        new JdbcRateLimitStoreAdapter(mock(JdbcClient.class, RETURNS_DEEP_STUBS));

    // when the window start is missing
    // then it fails
    assertThrows(
        NullPointerException.class, () -> adapter.incrementAndCount("agent-1", "search", null));
  }

  @Test
  void shouldRejectAStoreWithoutAJdbcClient() {
    // given no client
    // when the adapter is built
    // then it fails at wiring time
    assertThrows(NullPointerException.class, () -> new JdbcRateLimitStoreAdapter(null));
  }
}
