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
package io.github.tikyparkinson.mcpguardrails.approval.adapter.out.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalDecision;
import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalId;
import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalRequest;
import io.github.tikyparkinson.mcpguardrails.approval.domain.Approved;
import io.github.tikyparkinson.mcpguardrails.approval.domain.Rejected;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class InMemoryApprovalRequestAdapterTest {

  private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");
  private static final Duration SHORT_WAIT = Duration.ofMillis(200);
  private static final Duration LONG_WAIT = Duration.ofSeconds(10);
  private static final String AGENT = "agent-1";

  @Test
  void shouldAdmitRequestsUpToTheGlobalQuota() {
    // given a channel admitting three at a time
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(3, 3);

    // when four different agents ask
    List<Boolean> admitted =
        List.of(
            adapter.submit(requestFrom("a")),
            adapter.submit(requestFrom("b")),
            adapter.submit(requestFrom("c")),
            adapter.submit(requestFrom("d")));

    // then the fourth is turned away rather than queued
    assertEquals(List.of(true, true, true, false), admitted);
  }

  @Test
  void shouldAdmitRequestsUpToTheAgentQuota() {
    // given plenty of global room but two per agent
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(10, 2);

    // when one agent asks three times
    List<Boolean> admitted =
        List.of(
            adapter.submit(requestFrom(AGENT)),
            adapter.submit(requestFrom(AGENT)),
            adapter.submit(requestFrom(AGENT)));

    // then its third is turned away
    assertEquals(List.of(true, true, false), admitted);
  }

  @Test
  void shouldKeepOneAgentsQuotaFromAffectingAnother() {
    // given an agent that has already used up its own quota
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(10, 1);
    adapter.submit(requestFrom("noisy"));
    adapter.submit(requestFrom("noisy"));

    // when a different agent asks
    // then it is admitted: one looping agent must not take the approval channel away from
    // everybody else, which would be a denial of service against the guardrail itself
    assertTrue(adapter.submit(requestFrom("quiet")));
  }

  @Test
  void shouldNotConsumeGlobalQuotaWhenTheAgentQuotaRefuses() {
    // given an agent that keeps hitting its own limit
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(10, 1);
    adapter.submit(requestFrom("noisy"));
    for (int attempt = 0; attempt < 20; attempt++) {
      adapter.submit(requestFrom("noisy"));
    }

    // when nine other agents ask
    int admitted = 0;
    for (int index = 0; index < 9; index++) {
      if (adapter.submit(requestFrom("other-" + index))) {
        admitted++;
      }
    }

    // then all nine fit: a refused submission must hand its global slot straight back, or
    // repeated refusals would starve a channel that is holding almost nothing
    assertEquals(9, admitted);
  }

  @Test
  @Timeout(10)
  void shouldReturnTheDecisionToWhoeverIsWaiting() throws InterruptedException {
    // given a request being waited on
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(5, 5);
    ApprovalRequest request = requestFrom(AGENT);
    adapter.submit(request);
    AtomicReference<Optional<ApprovalDecision>> seen = new AtomicReference<>();
    CountDownLatch waiterDone = new CountDownLatch(1);
    Thread waiter =
        Thread.ofVirtual()
            .start(
                () -> {
                  seen.set(adapter.awaitDecision(request.id(), LONG_WAIT));
                  waiterDone.countDown();
                });

    // when a person decides
    awaitBlocked(waiter);
    adapter.resolve(request.id(), new Approved("alice"));

    // then the waiter wakes with that decision instead of sitting out the full deadline
    assertTrue(waiterDone.await(5, TimeUnit.SECONDS));
    assertEquals(Optional.of(new Approved("alice")), seen.get());
  }

  @Test
  @Timeout(10)
  void shouldReturnNothingWhenTheDeadlinePasses() {
    // given a request nobody decides
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(5, 5);
    ApprovalRequest request = requestFrom(AGENT);
    adapter.submit(request);

    // when the wait runs out
    // then the absence of an answer is reported as such, for the use case to deny on
    assertEquals(Optional.empty(), adapter.awaitDecision(request.id(), SHORT_WAIT));
  }

  @Test
  @Timeout(10)
  void shouldReleaseTheQuotaWhenTheDeadlinePasses() {
    // given a channel with a single slot, used by a request nobody decides
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(1, 1);
    ApprovalRequest request = requestFrom(AGENT);
    adapter.submit(request);
    adapter.awaitDecision(request.id(), SHORT_WAIT);

    // when another invocation asks
    // then the slot is free again, and the expired request is gone from the pending list: it
    // would otherwise hold quota for a caller that already left
    assertEquals(0, adapter.pendingCount());
    assertTrue(adapter.submit(requestFrom(AGENT)));
  }

  @Test
  @Timeout(10)
  void shouldReleaseTheQuotaWhenTheRequestIsDecided() {
    // given a channel with a single slot and a decided request
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(1, 1);
    ApprovalRequest request = requestFrom(AGENT);
    adapter.submit(request);
    adapter.resolve(request.id(), new Approved("alice"));
    adapter.awaitDecision(request.id(), SHORT_WAIT);

    // when another invocation asks
    // then the slot is free
    assertTrue(adapter.submit(requestFrom(AGENT)));
  }

  @Test
  void shouldKeepTheFirstDecisionWhenASecondArrives() {
    // given a request already refused by one person
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(5, 5);
    ApprovalRequest request = requestFrom(AGENT);
    adapter.submit(request);
    adapter.resolve(request.id(), new Rejected("bob", "not on prod"));

    // when somebody else tries to approve the same request
    boolean second = adapter.resolve(request.id(), new Approved("mallory"));

    // then the later attempt changes nothing: a refusal must not be overwritable by whoever
    // shouts last
    assertFalse(second);
    assertEquals(
        Optional.of(new Rejected("bob", "not on prod")),
        adapter.awaitDecision(request.id(), SHORT_WAIT));
  }

  @Test
  void shouldRefuseToResolveAnUnknownRequest() {
    // given a channel holding nothing
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(5, 5);

    // when a decision arrives for an identifier it never saw
    // then it reports that nothing happened rather than pretending it worked
    assertFalse(adapter.resolve(ApprovalId.newId(), new Approved("alice")));
  }

  @Test
  void shouldReturnNothingWhenWaitingForAnUnknownRequest() {
    // given a channel holding nothing
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(5, 5);

    // when somebody waits on an identifier it never saw
    // then it returns immediately with no answer
    assertEquals(Optional.empty(), adapter.awaitDecision(ApprovalId.newId(), LONG_WAIT));
  }

  @Test
  void shouldListPendingRequestsOldestFirst() {
    // given three requests escalated out of chronological order
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(5, 5);
    adapter.submit(requestAt("third", 300));
    adapter.submit(requestAt("first", 100));
    adapter.submit(requestAt("second", 200));

    // when the human side lists them
    List<String> tools = adapter.pending().stream().map(ApprovalRequest::toolName).toList();

    // then the oldest comes first, so whoever decides works through a queue rather than a heap
    assertEquals(List.of("first", "second", "third"), tools);
  }

  @Test
  void shouldForgetEverythingWhenCleared() {
    // given a populated channel
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(2, 2);
    adapter.submit(requestFrom(AGENT));
    adapter.submit(requestFrom(AGENT));

    // when cleared
    adapter.clear();

    // then both the requests and the quota they held are gone
    assertEquals(0, adapter.pendingCount());
    assertTrue(adapter.submit(requestFrom(AGENT)));
  }

  @Test
  @Timeout(30)
  void shouldAdmitExactlyTheQuotaWhenManyThreadsCompete() throws InterruptedException {
    // given fifty concurrent submissions for ten slots
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(10, 10);
    AtomicInteger admitted = new AtomicInteger();
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(50);
    for (int index = 0; index < 50; index++) {
      Thread.ofVirtual()
          .start(
              () -> {
                try {
                  start.await();
                  if (adapter.submit(requestFrom(AGENT))) {
                    admitted.incrementAndGet();
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

    // then the quota holds exactly: checking the count and then incrementing it would let two
    // threads both take the last slot
    assertEquals(10, admitted.get());
    assertEquals(10, adapter.pendingCount());
  }

  @Test
  @Timeout(30)
  void shouldWaitInParallelRatherThanOneAfterAnother() throws InterruptedException {
    // given ten requests, each waited on for the same deadline
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(20, 20);
    CountDownLatch finished = new CountDownLatch(10);
    for (int index = 0; index < 10; index++) {
      ApprovalRequest request = requestFrom("agent-" + index);
      adapter.submit(request);
      Thread.ofVirtual()
          .start(
              () -> {
                adapter.awaitDecision(request.id(), SHORT_WAIT);
                finished.countDown();
              });
    }

    // when they all wait at once
    long startedAt = System.nanoTime();
    assertTrue(finished.await(20, TimeUnit.SECONDS));
    long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

    // then the total is one deadline, not ten of them: waiting while holding a lock would
    // serialize every approval and let one pending invocation block all the others
    assertTrue(
        elapsedMillis < SHORT_WAIT.toMillis() * 5,
        "waits appear serialized: " + elapsedMillis + "ms for ten " + SHORT_WAIT + " waits");
  }

  @Test
  @Timeout(15)
  void shouldStopWaitingWhenTheThreadIsInterrupted() throws InterruptedException {
    // given an invocation waiting on a long deadline
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(5, 5);
    ApprovalRequest request = requestFrom(AGENT);
    adapter.submit(request);
    AtomicReference<Optional<ApprovalDecision>> seen = new AtomicReference<>();
    AtomicReference<Boolean> interruptFlag = new AtomicReference<>();
    CountDownLatch finished = new CountDownLatch(1);
    Thread waiter =
        Thread.ofVirtual()
            .start(
                () -> {
                  seen.set(adapter.awaitDecision(request.id(), Duration.ofMinutes(5)));
                  interruptFlag.set(Thread.currentThread().isInterrupted());
                  finished.countDown();
                });

    // when the server shuts the thread down
    awaitBlocked(waiter);
    waiter.interrupt();

    // then it stops waiting with no decision, and leaves the interrupt flag set so whoever owns
    // the thread can still see the shutdown it asked for
    assertTrue(finished.await(10, TimeUnit.SECONDS));
    assertEquals(Optional.empty(), seen.get());
    assertTrue(interruptFlag.get());
  }

  @Test
  @Timeout(15)
  void shouldReleaseOnlyOnceWhenTwoThreadsWaitOnTheSameRequest() throws InterruptedException {
    // given two threads waiting on one request, and one slot for the agent
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(4, 2);
    ApprovalRequest request = requestFrom(AGENT);
    adapter.submit(request);
    CountDownLatch finished = new CountDownLatch(2);
    for (int index = 0; index < 2; index++) {
      Thread.ofVirtual()
          .start(
              () -> {
                adapter.awaitDecision(request.id(), SHORT_WAIT);
                finished.countDown();
              });
    }

    // when both finish waiting
    assertTrue(finished.await(10, TimeUnit.SECONDS));

    // then the quota was given back once, not twice: a double release would let the channel
    // admit more than it should for ever after
    assertTrue(adapter.submit(requestFrom(AGENT)));
    assertTrue(adapter.submit(requestFrom(AGENT)));
    assertFalse(adapter.submit(requestFrom(AGENT)));
  }

  @Test
  @Timeout(15)
  void shouldKeepTheRemainingCountWhenAnAgentStillHasOtherRequests() {
    // given one agent holding two of its three slots
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(10, 3);
    ApprovalRequest first = requestFrom(AGENT);
    ApprovalRequest second = requestFrom(AGENT);
    adapter.submit(first);
    adapter.submit(second);
    adapter.submit(requestFrom(AGENT));

    // when only one of them is released
    adapter.awaitDecision(first.id(), SHORT_WAIT);

    // then the agent keeps the other two against its quota: releasing one must not reset the
    // count and hand it unlimited room
    assertTrue(adapter.submit(requestFrom(AGENT)));
    assertFalse(adapter.submit(requestFrom(AGENT)));
  }

  @Test
  void shouldRejectAGlobalQuotaBelowOne() {
    // given a channel that admits nothing
    // when built
    // then it fails: it could never approve anything
    assertThrows(IllegalArgumentException.class, () -> new InMemoryApprovalRequestAdapter(0, 1));
  }

  @Test
  void shouldRejectAnAgentQuotaBelowOne() {
    // given a per-agent quota of zero
    // when built
    // then it fails
    assertThrows(IllegalArgumentException.class, () -> new InMemoryApprovalRequestAdapter(10, 0));
  }

  @Test
  void shouldRejectAnAgentQuotaLargerThanTheGlobalOne() {
    // given a per-agent quota above the global one
    // when built
    // then it fails: it would promise room the channel does not have
    assertThrows(IllegalArgumentException.class, () -> new InMemoryApprovalRequestAdapter(5, 10));
  }

  @Test
  void shouldRejectSubmittingNothing() {
    // given no request
    // when submitted
    // then it fails
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(5, 5);
    assertThrows(NullPointerException.class, () -> adapter.submit(null));
  }

  @Test
  void shouldRejectWaitingWithoutAnIdentifier() {
    // given no identifier
    // when waited on
    // then it fails
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(5, 5);
    assertThrows(NullPointerException.class, () -> adapter.awaitDecision(null, SHORT_WAIT));
  }

  @Test
  void shouldRejectWaitingWithoutADeadline() {
    // given no deadline
    // when waited on
    // then it fails rather than waiting for ever on a held thread
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(5, 5);
    ApprovalId id = ApprovalId.newId();
    assertThrows(NullPointerException.class, () -> adapter.awaitDecision(id, null));
  }

  @Test
  void shouldRejectResolvingWithoutAnIdentifier() {
    // given no identifier
    // when a decision is recorded
    // then it fails
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(5, 5);
    Approved decision = new Approved("alice");
    assertThrows(NullPointerException.class, () -> adapter.resolve(null, decision));
  }

  @Test
  void shouldRejectResolvingWithoutADecision() {
    // given no decision
    // when recorded
    // then it fails
    InMemoryApprovalRequestAdapter adapter = new InMemoryApprovalRequestAdapter(5, 5);
    ApprovalId id = ApprovalId.newId();
    assertThrows(NullPointerException.class, () -> adapter.resolve(id, null));
  }

  /**
   * Blocks until the given thread is parked inside the wait, rather than guessing with a sleep: the
   * point of both tests is what happens to a thread that is already waiting.
   */
  private static void awaitBlocked(Thread thread) {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (thread.getState() != Thread.State.WAITING
        && thread.getState() != Thread.State.TIMED_WAITING
        && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
  }

  private static ApprovalRequest requestFrom(String agentId) {
    return new ApprovalRequest(
        ApprovalId.newId(), agentId, "delete_table", Map.of("table", "prod"), "escalated", NOW);
  }

  private static ApprovalRequest requestAt(String toolName, int secondsIn) {
    return new ApprovalRequest(
        ApprovalId.newId(), AGENT, toolName, Map.of(), "escalated", NOW.plusSeconds(secondsIn));
  }
}
