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
package io.github.tikyparkinson.mcpguardrails.anomaly.adapter.out.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.anomaly.domain.AgentHistory;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.ArgumentsFingerprint;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.InvocationRecord;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InMemoryInvocationHistoryAdapterTest {

  private static final Instant START = Instant.parse("2026-07-26T10:00:00Z");
  private static final Duration HALF_HOUR = Duration.ofMinutes(30);

  @Test
  void shouldReturnAnEmptyHistoryForAnAgentNeverSeen() {
    // given an adapter holding nothing
    InMemoryInvocationHistoryAdapter adapter = new InMemoryInvocationHistoryAdapter(HALF_HOUR, 500);

    // when an unknown agent is read
    AgentHistory history = adapter.historyOf("stranger", START);

    // then the history is empty rather than null
    assertEquals(new AgentHistory(java.util.List.of(), Set.of(), 0L), history);
  }

  @Test
  void shouldSplitTheHistoryAtTheWindowStart() {
    // given three calls, one of them before the window
    InMemoryInvocationHistoryAdapter adapter = new InMemoryInvocationHistoryAdapter(HALF_HOUR, 500);
    adapter.record(record("old", 0));
    adapter.record(record("recent", 100));
    adapter.record(record("newer", 110));

    // when read from second 50 onwards
    AgentHistory history = adapter.historyOf("agent", START.plusSeconds(50));

    // then the older call becomes baseline and the rest the window
    assertEquals(2, history.withinWindow().size());
    assertEquals(Set.of("old"), history.toolsBeforeWindow());
    assertEquals(1L, history.invocationsBeforeWindow());
  }

  @Test
  void shouldIncludeARecordLandingExactlyOnTheWindowStart() {
    // given a call at the very instant the window opens
    InMemoryInvocationHistoryAdapter adapter = new InMemoryInvocationHistoryAdapter(HALF_HOUR, 500);
    adapter.record(record("edge", 60));

    // when read from that same instant
    AgentHistory history = adapter.historyOf("agent", START.plusSeconds(60));

    // then it counts as inside: the window start is inclusive
    assertEquals(1, history.withinWindow().size());
  }

  @Test
  void shouldFoldTheOldestRecordsIntoTheBaselineWhenTheCapIsReached() {
    // given ten calls to ten different tools with room for only three records
    InMemoryInvocationHistoryAdapter adapter = new InMemoryInvocationHistoryAdapter(HALF_HOUR, 3);
    for (int index = 0; index < 10; index++) {
      adapter.record(record("tool" + index, index));
    }

    // when the whole history is read as baseline
    AgentHistory history = adapter.historyOf("agent", START.plusSeconds(500));

    // then nothing was lost: dropping the overflow instead of folding it would erase the baseline
    // exactly for the agent enumerating hundreds of tools, leaving it looking like a newcomer
    assertEquals(10L, history.invocationsBeforeWindow());
    assertEquals(10, history.toolsBeforeWindow().size());
    assertTrue(history.toolsBeforeWindow().contains("tool0"));
  }

  @Test
  void shouldKeepOnlyTheCapInDetailedRecords() {
    // given ten calls with room for only three records
    InMemoryInvocationHistoryAdapter adapter = new InMemoryInvocationHistoryAdapter(HALF_HOUR, 3);
    for (int index = 0; index < 10; index++) {
      adapter.record(record("tool" + index, index));
    }

    // when the window covers everything
    AgentHistory history = adapter.historyOf("agent", START);

    // then only the cap is held in detail: the fingerprints are the expensive part
    assertEquals(3, history.withinWindow().size());
  }

  @Test
  void shouldForgetRecordsOlderThanTheRetention() {
    // given a one minute retention and a call five minutes before the next one
    InMemoryInvocationHistoryAdapter adapter =
        new InMemoryInvocationHistoryAdapter(Duration.ofMinutes(1), 500);
    adapter.record(record("old", 0));
    adapter.record(record("recent", 300));

    // when read
    AgentHistory history = adapter.historyOf("agent", START.plusSeconds(299));

    // then the expired call is gone from both the window and the baseline
    assertEquals(1, history.withinWindow().size());
    assertEquals(0L, history.invocationsBeforeWindow());
  }

  @Test
  void shouldForgetTheFoldedBaselineOnceItIsOlderThanTheRetention() {
    // given records folded into the baseline and then a long silence
    InMemoryInvocationHistoryAdapter adapter =
        new InMemoryInvocationHistoryAdapter(Duration.ofMinutes(1), 2);
    adapter.record(record("a", 0));
    adapter.record(record("b", 1));
    adapter.record(record("c", 2));
    adapter.record(record("later", 600));

    // when read after the retention has passed
    AgentHistory history = adapter.historyOf("agent", START.plusSeconds(599));

    // then the summary expired too: retention bounds both kinds of data, or an idle agent would
    // keep its baseline for ever
    assertEquals(0L, history.invocationsBeforeWindow());
    assertEquals(Set.of(), history.toolsBeforeWindow());
  }

  @Test
  void shouldKeepAgentsApart() {
    // given two agents calling different tools
    InMemoryInvocationHistoryAdapter adapter = new InMemoryInvocationHistoryAdapter(HALF_HOUR, 500);
    adapter.record(new InvocationRecord("one", "search", fingerprint(1), START));
    adapter.record(new InvocationRecord("two", "delete", fingerprint(2), START));

    // when each is read
    // then neither sees the other's calls
    assertEquals(1, adapter.historyOf("one", START).withinWindow().size());
    assertEquals(2, adapter.trackedAgents());
  }

  @Test
  void shouldForgetEverythingWhenCleared() {
    // given a populated adapter
    InMemoryInvocationHistoryAdapter adapter = new InMemoryInvocationHistoryAdapter(HALF_HOUR, 500);
    adapter.record(record("search", 0));

    // when cleared
    adapter.clear();

    // then no agent is tracked any more
    assertEquals(0, adapter.trackedAgents());
    assertEquals(0, adapter.historyOf("agent", START).withinWindow().size());
  }

  @Test
  void shouldRemainConsistentUnderConcurrentWriters() throws InterruptedException {
    // given eight threads recording into the same agent's window
    InMemoryInvocationHistoryAdapter adapter =
        new InMemoryInvocationHistoryAdapter(Duration.ofHours(1), 10_000);
    int threads = 8;
    int perThread = 500;
    AtomicInteger failures = new AtomicInteger();
    CountDownLatch done = new CountDownLatch(threads);
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      for (int thread = 0; thread < threads; thread++) {
        final int id = thread;
        pool.submit(
            () -> {
              try {
                for (int index = 0; index < perThread; index++) {
                  adapter.record(
                      new InvocationRecord("agent", "tool" + id, fingerprint(index), START));
                }
              } catch (RuntimeException e) {
                failures.incrementAndGet();
              } finally {
                done.countDown();
              }
            });
      }
      assertTrue(done.await(30, TimeUnit.SECONDS));
    }

    // when the window is read
    // then every write survived: an ArrayDeque mutated without the lock would lose writes or
    // corrupt its own state
    assertEquals(0, failures.get());
    assertEquals(threads * perThread, adapter.historyOf("agent", START).withinWindow().size());
  }

  @Test
  void shouldEmptyTheWindowWhenEveryRecordHasExpired() {
    // given an agent that went quiet long ago
    InMemoryInvocationHistoryAdapter adapter =
        new InMemoryInvocationHistoryAdapter(Duration.ofMinutes(1), 500);
    adapter.record(record("search", 0));
    adapter.record(record("search", 5));

    // when it is read hours later
    AgentHistory history = adapter.historyOf("agent", START.plusSeconds(10_000));

    // then everything is gone, window and baseline alike
    assertEquals(0, history.withinWindow().size());
    assertEquals(0L, history.invocationsBeforeWindow());
  }

  @Test
  void shouldRejectANonPositiveRetention() {
    // given a retention of zero
    // when the adapter is built
    // then it fails: nothing would ever be kept
    assertThrows(
        IllegalArgumentException.class,
        () -> new InMemoryInvocationHistoryAdapter(Duration.ZERO, 500));
  }

  @Test
  void shouldRejectANegativeRetention() {
    // given a retention pointing backwards
    // when the adapter is built
    // then it fails
    assertThrows(
        IllegalArgumentException.class,
        () -> new InMemoryInvocationHistoryAdapter(Duration.ofMinutes(-1), 500));
  }

  @Test
  void shouldKeepTheLatestFoldedInstantWhenRecordsArriveOutOfOrder() {
    // given a later call recorded before an earlier one, as two threads can produce
    InMemoryInvocationHistoryAdapter adapter =
        new InMemoryInvocationHistoryAdapter(Duration.ofMinutes(10), 1);
    adapter.record(record("late", 300));
    adapter.record(record("early", 100));
    adapter.record(record("last", 310));

    // when read well after the earlier call but within retention of the later one
    AgentHistory history = adapter.historyOf("agent", START.plusSeconds(305));

    // then the baseline still holds both folded calls: expiry follows the latest instant folded,
    // so one out-of-order record cannot expire the whole summary early
    assertEquals(2L, history.invocationsBeforeWindow());
    assertEquals(Set.of("late", "early"), history.toolsBeforeWindow());
  }

  @Test
  void shouldRejectARetentionThatIsNull() {
    // given no retention
    // when the adapter is built
    // then it fails
    assertThrows(NullPointerException.class, () -> new InMemoryInvocationHistoryAdapter(null, 500));
  }

  @Test
  void shouldRejectACapBelowOne() {
    // given a cap of zero
    // when the adapter is built
    // then it fails
    assertThrows(
        IllegalArgumentException.class, () -> new InMemoryInvocationHistoryAdapter(HALF_HOUR, 0));
  }

  @Test
  void shouldRejectRecordingNothing() {
    // given no record
    // when stored
    // then it fails
    InMemoryInvocationHistoryAdapter adapter = new InMemoryInvocationHistoryAdapter(HALF_HOUR, 500);
    assertThrows(NullPointerException.class, () -> adapter.record(null));
  }

  @Test
  void shouldRejectReadingWithoutAWindowStart() {
    // given no window start
    // when read
    // then it fails
    InMemoryInvocationHistoryAdapter adapter = new InMemoryInvocationHistoryAdapter(HALF_HOUR, 500);
    assertThrows(NullPointerException.class, () -> adapter.historyOf("agent", null));
  }

  @Test
  void shouldRejectReadingWithoutAnAgent() {
    // given no agent
    // when read
    // then it fails
    InMemoryInvocationHistoryAdapter adapter = new InMemoryInvocationHistoryAdapter(HALF_HOUR, 500);
    assertThrows(NullPointerException.class, () -> adapter.historyOf(null, START));
  }

  private static InvocationRecord record(String tool, int secondsIn) {
    return new InvocationRecord(
        "agent", tool, fingerprint(secondsIn), START.plusSeconds(secondsIn));
  }

  private static ArgumentsFingerprint fingerprint(int seed) {
    return ArgumentsFingerprint.of(Map.of("seed", seed));
  }
}
