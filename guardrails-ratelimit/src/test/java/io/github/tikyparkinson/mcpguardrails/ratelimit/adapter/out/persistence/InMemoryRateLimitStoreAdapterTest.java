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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class InMemoryRateLimitStoreAdapterTest {

  private static final Instant WINDOW_A = Instant.parse("2026-07-24T10:05:00Z");
  private static final Instant WINDOW_B = Instant.parse("2026-07-24T10:06:00Z");

  private final InMemoryRateLimitStoreAdapter store = new InMemoryRateLimitStoreAdapter();

  @Test
  void shouldCountIndependentlyWhenKeysDiffer() {
    // given / when
    store.incrementAndCount("agent-1", "search", WINDOW_A);
    store.incrementAndCount("agent-1", "search", WINDOW_A);
    long other = store.incrementAndCount("agent-2", "search", WINDOW_A);
    long third = store.incrementAndCount("agent-1", "delete", WINDOW_A);

    // then
    assertEquals(3, store.incrementAndCount("agent-1", "search", WINDOW_A));
    assertEquals(1, other);
    assertEquals(1, third);
  }

  @Test
  void shouldResetCountWhenNewWindowStarts() {
    // given
    store.incrementAndCount("agent-1", "search", WINDOW_A);
    store.incrementAndCount("agent-1", "search", WINDOW_A);

    // when: touching the next window evicts the old one for that key
    long fresh = store.incrementAndCount("agent-1", "search", WINDOW_B);

    // then
    assertEquals(1, fresh);
  }

  @Test
  void shouldNotEvictOtherKeysWhenOneKeyAdvancesWindow() {
    // given
    store.incrementAndCount("agent-2", "search", WINDOW_A);

    // when: agent-1 advances to window B; agent-2's window A counter must survive
    store.incrementAndCount("agent-1", "search", WINDOW_B);

    // then
    assertEquals(2, store.incrementAndCount("agent-2", "search", WINDOW_A));
  }

  @Test
  void shouldNotLoseIncrementsWhenCalledConcurrently() throws Exception {
    // given
    int threads = 8;
    int perThread = 250;
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      List<Callable<Long>> tasks = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        tasks.add(
            () -> {
              long last = 0;
              for (int i = 0; i < perThread; i++) {
                last = store.incrementAndCount("agent-1", "search", WINDOW_A);
              }
              return last;
            });
      }

      // when
      for (Future<Long> future : pool.invokeAll(tasks)) {
        future.get();
      }
    }

    // then: no lost updates
    assertEquals(threads * perThread + 1L, store.incrementAndCount("agent-1", "search", WINDOW_A));
  }

  @Test
  void shouldRejectNullInputsWhenIncrementing() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> store.incrementAndCount(null, "tool", WINDOW_A));
    assertThrows(
        NullPointerException.class, () -> store.incrementAndCount("agent", null, WINDOW_A));
    assertThrows(NullPointerException.class, () -> store.incrementAndCount("agent", "tool", null));
  }
}
