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
package io.github.tikyparkinson.mcpguardrails.trifecta.adapter.out.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.trifecta.domain.Capability;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.SessionAccumulation;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.SessionId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class InMemorySessionCapabilityAdapterTest {

  private static final Instant START = Instant.parse("2026-07-26T10:00:00Z");
  private static final Duration HALF_HOUR = Duration.ofMinutes(30);
  private static final Duration TWO_HOURS = Duration.ofHours(2);
  private static final SessionId SESSION = SessionId.ofMcpSession("s1");
  private static final Set<Capability> PRIVATE = Set.of(Capability.PRIVATE_DATA);
  private static final Set<Capability> UNTRUSTED = Set.of(Capability.UNTRUSTED_CONTENT);
  private static final Set<Capability> EXTERNAL = Set.of(Capability.EXTERNAL_COMMS);
  private static final Set<Capability> ALL_THREE = Set.of(Capability.values());

  @Test
  void shouldStartASessionOnItsFirstInvocation() {
    // given an empty store
    InMemorySessionCapabilityAdapter adapter = adapter();

    // when the first invocation arrives
    SessionAccumulation result = adapter.accumulate(SESSION, PRIVATE, START);

    // then the session exists holding what that invocation contributed
    assertEquals(PRIVATE, result.session().capabilities());
    assertEquals(START, result.session().startedAt());
    assertFalse(result.completeBefore());
  }

  @Test
  void shouldAccumulateAcrossInvocations() {
    // given a session that saw two legs
    InMemorySessionCapabilityAdapter adapter = adapter();
    adapter.accumulate(SESSION, PRIVATE, START);
    adapter.accumulate(SESSION, UNTRUSTED, START.plusSeconds(10));

    // when a third invocation adds the last one
    SessionAccumulation result = adapter.accumulate(SESSION, EXTERNAL, START.plusSeconds(20));

    // then the three are held together
    assertEquals(ALL_THREE, result.session().capabilities());
  }

  @Test
  void shouldReportTheInvocationThatClosedTheTriangle() {
    // given a session one leg short
    InMemorySessionCapabilityAdapter adapter = adapter();
    adapter.accumulate(SESSION, PRIVATE, START);
    adapter.accumulate(SESSION, UNTRUSTED, START);

    // when the missing leg arrives
    // then this invocation is the one that closed it
    assertTrue(adapter.accumulate(SESSION, EXTERNAL, START).closedNow());
  }

  @Test
  void shouldNotReportAClosureOnAnAlreadyCompleteSession() {
    // given a session that already holds the three legs
    InMemorySessionCapabilityAdapter adapter = adapter();
    adapter.accumulate(SESSION, ALL_THREE, START);

    // when another invocation contributes a leg it already had
    // then it escalates all the same but claims no closure: the difference cannot be worked out
    // from the resulting state, which is why the port reports it
    assertFalse(adapter.accumulate(SESSION, EXTERNAL, START).closedNow());
  }

  @Test
  void shouldKeepTheOriginalStartAcrossInvocations() {
    // given a session started at a known instant
    InMemorySessionCapabilityAdapter adapter = adapter();
    adapter.accumulate(SESSION, PRIVATE, START);

    // when a later invocation arrives
    SessionAccumulation result = adapter.accumulate(SESSION, UNTRUSTED, START.plusSeconds(600));

    // then the start does not move: refreshing it would make the absolute bound unreachable
    assertEquals(START, result.session().startedAt());
  }

  @Test
  void shouldKeepSessionsApart() {
    // given two sessions of the same agent
    InMemorySessionCapabilityAdapter adapter = adapter();
    adapter.accumulate(SessionId.ofMcpSession("a"), ALL_THREE, START);

    // when the other one makes its first call
    SessionAccumulation other = adapter.accumulate(SessionId.ofMcpSession("b"), PRIVATE, START);

    // then it is unaffected: one user closing the triangle must not escalate everybody else's work
    assertEquals(PRIVATE, other.session().capabilities());
    assertEquals(2, adapter.trackedSessions());
  }

  @Test
  void shouldForgetASessionThatWentQuiet() {
    // given a session that saw the three legs and then stopped
    InMemorySessionCapabilityAdapter adapter = adapter();
    adapter.accumulate(SESSION, ALL_THREE, START);

    // when it comes back after the idle timeout
    SessionAccumulation result = adapter.accumulate(SESSION, PRIVATE, START.plusSeconds(2_700));

    // then it starts over: an agent that stopped working is no longer the same run
    assertEquals(PRIVATE, result.session().capabilities());
  }

  @Test
  void shouldKeepASessionThatCameBackInTime() {
    // given a session that saw the three legs
    InMemorySessionCapabilityAdapter adapter = adapter();
    adapter.accumulate(SESSION, ALL_THREE, START);

    // when it comes back before the idle timeout
    SessionAccumulation result = adapter.accumulate(SESSION, PRIVATE, START.plusSeconds(600));

    // then it is the same session and still closed
    assertTrue(result.session().hasTrifecta());
    assertTrue(result.completeBefore());
  }

  @Test
  void shouldForgetASessionThatNeverStops() {
    // given an agent invoking every ten seconds, which refreshes the idle clock every time
    InMemorySessionCapabilityAdapter adapter = adapter();
    adapter.accumulate(SESSION, ALL_THREE, START);
    Instant now = START;
    boolean restarted = false;
    for (int call = 1; call <= 8_640 && !restarted; call++) {
      now = now.plusSeconds(10);
      restarted = adapter.accumulate(SESSION, Set.of(), now).session().capabilities().isEmpty();
    }

    // when the absolute bound is reached
    // then the session restarts anyway. Without this the idle bound alone never fires on a busy
    // agent, and a trifecta closed in the morning would still be escalating the next day
    assertTrue(restarted, "a busy session never expired");
    Duration lived = Duration.between(START, now);
    assertTrue(
        lived.compareTo(TWO_HOURS) >= 0 && lived.compareTo(TWO_HOURS.plusMinutes(1)) < 0,
        "expired after " + lived + ", expected just past the two-hour bound");
  }

  @Test
  void shouldListSessionsWithAClosedTrifectaOldestFirst() {
    // given three sessions, only two of them closed, opened out of order
    InMemorySessionCapabilityAdapter adapter = adapter();
    adapter.accumulate(SessionId.ofMcpSession("late"), ALL_THREE, START.plusSeconds(200));
    adapter.accumulate(SessionId.ofMcpSession("early"), ALL_THREE, START.plusSeconds(100));
    adapter.accumulate(SessionId.ofMcpSession("open"), PRIVATE, START);

    // when the human side lists what is locked
    List<SessionId> locked = adapter.withTrifecta();

    // then only the closed ones appear, oldest first, so whoever reviews works through a queue
    assertEquals(List.of(SessionId.ofMcpSession("early"), SessionId.ofMcpSession("late")), locked);
  }

  @Test
  void shouldReturnNoLockedSessionsWhenNoneIsClosed() {
    // given a store with an open session
    InMemorySessionCapabilityAdapter adapter = adapter();
    adapter.accumulate(SESSION, PRIVATE, START);

    // when the locked ones are listed
    // then the list is empty rather than null
    assertEquals(List.of(), adapter.withTrifecta());
  }

  @Test
  void shouldForgetASessionWhenAskedTo() {
    // given a session with a closed trifecta
    InMemorySessionCapabilityAdapter adapter = adapter();
    adapter.accumulate(SESSION, ALL_THREE, START);

    // when a person resets it
    boolean forgotten = adapter.forget(SESSION);

    // then it starts from nothing again, which is the only way out short of waiting for expiry
    assertTrue(forgotten);
    assertEquals(PRIVATE, adapter.accumulate(SESSION, PRIVATE, START).session().capabilities());
  }

  @Test
  void shouldReportWhenThereWasNothingToForget() {
    // given a store that never saw the session
    InMemorySessionCapabilityAdapter adapter = adapter();

    // when it is reset
    // then it says nothing changed
    assertFalse(adapter.forget(SESSION));
  }

  @Test
  void shouldForgetEverythingWhenCleared() {
    // given two tracked sessions
    InMemorySessionCapabilityAdapter adapter = adapter();
    adapter.accumulate(SessionId.ofMcpSession("a"), ALL_THREE, START);
    adapter.accumulate(SessionId.ofMcpSession("b"), PRIVATE, START);

    // when cleared
    adapter.clear();

    // then none is tracked
    assertEquals(0, adapter.trackedSessions());
  }

  @Test
  @Timeout(30)
  void shouldLetExactlyOneInvocationClaimTheClosure() throws InterruptedException {
    // given a session one leg short and a hundred concurrent invocations supplying it
    InMemorySessionCapabilityAdapter adapter = adapter();
    adapter.accumulate(SESSION, PRIVATE, START);
    adapter.accumulate(SESSION, UNTRUSTED, START);
    AtomicInteger claims = new AtomicInteger();
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(100);
    for (int index = 0; index < 100; index++) {
      Thread.ofVirtual()
          .start(
              () -> {
                try {
                  start.await();
                  if (adapter.accumulate(SESSION, EXTERNAL, START).closedNow()) {
                    claims.incrementAndGet();
                  }
                } catch (InterruptedException _) {
                  Thread.currentThread().interrupt();
                } finally {
                  finished.countDown();
                }
              });
    }

    // when they all go at once
    start.countDown();
    assertTrue(finished.await(20, TimeUnit.SECONDS));

    // then only one says it closed the triangle: reading the session and writing it back outside
    // an atomic step would have every one of them claim it, and the reason would be wrong 99 times
    assertEquals(1, claims.get());
  }

  @Test
  @Timeout(30)
  void shouldLoseNoContributionUnderConcurrentWriters() throws InterruptedException {
    // given three threads, each contributing a different leg many times over
    InMemorySessionCapabilityAdapter adapter = adapter();
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(3);
    for (Set<Capability> leg : List.of(PRIVATE, UNTRUSTED, EXTERNAL)) {
      Thread.ofVirtual()
          .start(
              () -> {
                try {
                  start.await();
                  for (int call = 0; call < 200; call++) {
                    adapter.accumulate(SESSION, leg, START);
                  }
                } catch (InterruptedException _) {
                  Thread.currentThread().interrupt();
                } finally {
                  finished.countDown();
                }
              });
    }

    // when they all write at once
    start.countDown();
    assertTrue(finished.await(20, TimeUnit.SECONDS));

    // then every leg survived: a lost update here would hide a trifecta that did happen
    assertEquals(ALL_THREE, adapter.accumulate(SESSION, Set.of(), START).session().capabilities());
  }

  @Test
  void shouldRejectANonPositiveIdleTimeout() {
    // given an idle timeout of zero
    // when the adapter is built
    // then it fails: every session would expire before its second invocation
    assertThrows(
        IllegalArgumentException.class,
        () -> new InMemorySessionCapabilityAdapter(Duration.ZERO, TWO_HOURS));
  }

  @Test
  void shouldRejectAnIdleTimeoutPointingBackwards() {
    // given a negative idle timeout
    // when the adapter is built
    // then it fails
    Duration backwards = Duration.ofMinutes(-1);
    assertThrows(
        IllegalArgumentException.class,
        () -> new InMemorySessionCapabilityAdapter(backwards, TWO_HOURS));
  }

  @Test
  void shouldRejectANonPositiveMaximumDuration() {
    // given a maximum duration of zero
    // when the adapter is built
    // then it fails
    assertThrows(
        IllegalArgumentException.class,
        () -> new InMemorySessionCapabilityAdapter(HALF_HOUR, Duration.ZERO));
  }

  @Test
  void shouldRejectAMaximumDurationShorterThanTheIdleTimeout() {
    // given an absolute bound shorter than the idle one
    // when the adapter is built
    // then it fails: the idle bound would be unreachable
    assertThrows(
        IllegalArgumentException.class,
        () -> new InMemorySessionCapabilityAdapter(TWO_HOURS, HALF_HOUR));
  }

  @Test
  void shouldRejectAnAdapterWithoutAnIdleTimeout() {
    // given no idle timeout
    // when the adapter is built
    // then it fails
    assertThrows(
        NullPointerException.class, () -> new InMemorySessionCapabilityAdapter(null, TWO_HOURS));
  }

  @Test
  void shouldRejectAnAdapterWithoutAMaximumDuration() {
    // given no maximum duration
    // when the adapter is built
    // then it fails
    assertThrows(
        NullPointerException.class, () -> new InMemorySessionCapabilityAdapter(HALF_HOUR, null));
  }

  @Test
  void shouldRejectAccumulatingWithoutASession() {
    // given no session
    // when accumulated
    // then it fails
    InMemorySessionCapabilityAdapter adapter = adapter();
    assertThrows(NullPointerException.class, () -> adapter.accumulate(null, PRIVATE, START));
  }

  @Test
  void shouldRejectAccumulatingWithoutCapabilities() {
    // given no capability set at all
    // when accumulated
    // then it fails rather than quietly meaning "nothing contributed", a different thing
    InMemorySessionCapabilityAdapter adapter = adapter();
    assertThrows(NullPointerException.class, () -> adapter.accumulate(SESSION, null, START));
  }

  @Test
  void shouldRejectAccumulatingWithoutAnInstant() {
    // given no instant
    // when accumulated
    // then it fails: expiry could not be measured
    InMemorySessionCapabilityAdapter adapter = adapter();
    assertThrows(NullPointerException.class, () -> adapter.accumulate(SESSION, PRIVATE, null));
  }

  @Test
  void shouldRejectForgettingWithoutASession() {
    // given no session
    // when forgotten
    // then it fails
    InMemorySessionCapabilityAdapter adapter = adapter();
    assertThrows(NullPointerException.class, () -> adapter.forget(null));
  }

  private static InMemorySessionCapabilityAdapter adapter() {
    return new InMemorySessionCapabilityAdapter(HALF_HOUR, TWO_HOURS);
  }
}
