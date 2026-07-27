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

import io.github.tikyparkinson.mcpguardrails.approval.application.port.out.ApprovalRequestPort;
import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalDecision;
import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalId;
import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalRequest;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default approval channel, held in this process. Requests wait on a latch that whoever decides
 * opens.
 *
 * <p>Two quotas bound it. The global one protects the server's threads, since every wait holds one;
 * the per-agent one stops a single looping agent from filling the channel and leaving everybody
 * else unable to get anything approved, which would be a denial of service against the approval
 * mechanism itself. Hitting either is a rejection, never a pass.
 *
 * <p>Requests live only here: a restart loses every wait in flight, though nothing is left dangling
 * because the MCP calls waiting on them die with the process. Behind a load balancer a request is
 * only visible on the replica that created it, so the human channel must reach that replica or this
 * adapter must be replaced by a shared one.
 */
public final class InMemoryApprovalRequestAdapter implements ApprovalRequestPort {

  private final int maxPending;
  private final int maxPendingPerAgent;
  private final Map<ApprovalId, PendingApproval> pending = new ConcurrentHashMap<>();
  private final Map<String, Integer> perAgent = new ConcurrentHashMap<>();
  private final AtomicInteger held = new AtomicInteger();

  /**
   * @param maxPending requests admitted at once; at least 1
   * @param maxPendingPerAgent cap per agent; at least 1 and no greater than {@code maxPending}
   */
  public InMemoryApprovalRequestAdapter(int maxPending, int maxPendingPerAgent) {
    if (maxPending < 1) {
      throw new IllegalArgumentException("maxPending must be at least 1, was " + maxPending);
    }
    if (maxPendingPerAgent < 1) {
      throw new IllegalArgumentException(
          "maxPendingPerAgent must be at least 1, was " + maxPendingPerAgent);
    }
    if (maxPendingPerAgent > maxPending) {
      throw new IllegalArgumentException(
          "maxPendingPerAgent (%d) must not exceed maxPending (%d)"
              .formatted(maxPendingPerAgent, maxPending));
    }
    this.maxPending = maxPending;
    this.maxPendingPerAgent = maxPendingPerAgent;
  }

  /**
   * Takes a slot from each quota, or takes nothing at all. If the per-agent quota refuses, the
   * global slot is handed straight back: a rejected submission must not leave capacity consumed
   * behind it, or repeated rejections would starve the channel without anything pending in it.
   */
  @Override
  public boolean submit(ApprovalRequest request) {
    Objects.requireNonNull(request, "request");
    if (!acquireGlobalSlot()) {
      return false;
    }
    if (!acquireAgentSlot(request.agentId())) {
      held.decrementAndGet();
      return false;
    }
    pending.put(request.id(), new PendingApproval(request));
    return true;
  }

  /**
   * Waits outside any lock of this adapter. The wait lasts as long as the timeout, and holding a
   * monitor across it would serialize every approval: one pending invocation would block all the
   * others for the full deadline, turning the quotas into decoration.
   */
  @Override
  public Optional<ApprovalDecision> awaitDecision(ApprovalId id, Duration timeout) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(timeout, "timeout");
    PendingApproval approval = pending.get(id);
    if (approval == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(approval.decision().get(timeout.toMillis(), TimeUnit.MILLISECONDS));
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (ExecutionException | TimeoutException _) {
      return Optional.empty();
    } finally {
      release(id);
    }
  }

  /**
   * Completing the pending decision is what makes the first answer win: a second call finds it
   * already completed and changes nothing, so a refusal cannot be overwritten by whoever speaks
   * last.
   */
  @Override
  public boolean resolve(ApprovalId id, ApprovalDecision decision) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(decision, "decision");
    PendingApproval approval = pending.get(id);
    return approval != null && approval.decision().complete(decision);
  }

  @Override
  public List<ApprovalRequest> pending() {
    return pending.values().stream()
        .map(PendingApproval::request)
        .sorted(Comparator.comparing(ApprovalRequest::requestedAt))
        .toList();
  }

  /** Forgets every pending request without deciding any of them. */
  public void clear() {
    pending.clear();
    perAgent.clear();
    held.set(0);
  }

  /** Number of requests currently waiting, so callers can watch the channel's occupancy. */
  public int pendingCount() {
    return pending.size();
  }

  private boolean acquireGlobalSlot() {
    return held.getAndUpdate(current -> current < maxPending ? current + 1 : current) < maxPending;
  }

  /**
   * Both the check and the increment happen inside {@code compute}, which {@link ConcurrentHashMap}
   * runs atomically for the key. Reading the count and then incrementing it would let two threads
   * of the same agent both see room for the last slot and take it.
   */
  private boolean acquireAgentSlot(String agentId) {
    boolean[] acquired = {false};
    perAgent.compute(
        agentId,
        (unused, count) -> {
          int current = count == null ? 0 : count;
          if (current >= maxPendingPerAgent) {
            return current;
          }
          acquired[0] = true;
          return current + 1;
        });
    return acquired[0];
  }

  /**
   * Drops the request, whether it was decided or the deadline passed. Leaving an expired request in
   * place would hold quota for a caller that already left, and would show a human a decision they
   * can no longer influence.
   */
  private void release(ApprovalId id) {
    PendingApproval removed = pending.remove(id);
    if (removed == null) {
      return;
    }
    held.decrementAndGet();
    perAgent.computeIfPresent(
        removed.request().agentId(), (unused, count) -> count <= 1 ? null : count - 1);
  }

  private record PendingApproval(
      ApprovalRequest request, CompletableFuture<ApprovalDecision> decision) {

    PendingApproval(ApprovalRequest request) {
      this(request, new CompletableFuture<>());
    }
  }
}
