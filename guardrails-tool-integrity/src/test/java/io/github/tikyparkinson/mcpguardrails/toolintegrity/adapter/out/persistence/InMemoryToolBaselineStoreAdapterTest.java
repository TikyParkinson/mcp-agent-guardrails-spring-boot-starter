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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.ToolFingerprint;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class InMemoryToolBaselineStoreAdapterTest {

  private static final ToolFingerprint FP_A = new ToolFingerprint("a".repeat(64));
  private static final ToolFingerprint FP_B = new ToolFingerprint("b".repeat(64));

  private final InMemoryToolBaselineStoreAdapter store = new InMemoryToolBaselineStoreAdapter();

  @Test
  void shouldReturnEmptyWhenNoBaselineStored() {
    // given / when / then
    assertEquals(Optional.empty(), store.find("search"));
  }

  @Test
  void shouldKeepFirstFingerprintWhenEstablishingTwice() {
    // given: TOFU — the first one wins
    assertEquals(FP_A, store.establishIfAbsent("search", FP_A));

    // when
    ToolFingerprint winner = store.establishIfAbsent("search", FP_B);

    // then
    assertEquals(FP_A, winner);
    assertEquals(Optional.of(FP_A), store.find("search"));
  }

  @Test
  void shouldOverwriteBaselineWhenReplaced() {
    // given
    store.establishIfAbsent("search", FP_A);

    // when: the approval flow replaces
    store.replace("search", FP_B);

    // then
    assertEquals(Optional.of(FP_B), store.find("search"));
  }

  @Test
  void shouldElectExactlyOneWinnerWhenEstablishingConcurrently() throws Exception {
    // given: many threads race to establish different fingerprints for the same tool
    int threads = 8;
    List<ToolFingerprint> candidates = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      candidates.add(new ToolFingerprint(Integer.toHexString(i).repeat(64).substring(0, 64)));
    }
    Set<ToolFingerprint> winners = new HashSet<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      List<Callable<ToolFingerprint>> tasks = new ArrayList<>();
      for (ToolFingerprint candidate : candidates) {
        tasks.add(() -> store.establishIfAbsent("search", candidate));
      }

      // when
      for (Future<ToolFingerprint> future : pool.invokeAll(tasks)) {
        winners.add(future.get());
      }
    }

    // then: every thread observed the same single winner
    assertEquals(1, winners.size());
    assertTrue(candidates.contains(winners.iterator().next()));
  }

  @Test
  void shouldRejectNullInputsWhenInvoked() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> store.find(null));
    assertThrows(NullPointerException.class, () -> store.establishIfAbsent(null, FP_A));
    assertThrows(NullPointerException.class, () -> store.establishIfAbsent("t", null));
    assertThrows(NullPointerException.class, () -> store.replace(null, FP_A));
    assertThrows(NullPointerException.class, () -> store.replace("t", null));
  }
}
