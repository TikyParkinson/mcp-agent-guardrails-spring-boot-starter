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

import io.github.tikyparkinson.mcpguardrails.anomaly.application.port.out.InvocationHistoryPort;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.AgentHistory;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.InvocationRecord;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default history, kept in this process. Fine for a single instance; behind a load balancer each
 * replica sees only its own slice, so an agent spread across replicas is harder to catch. Implement
 * {@link InvocationHistoryPort} against a shared store to fix that.
 *
 * <p>Two things are kept per agent, with different retentions, because they cost different things
 * and are worth keeping for different lengths of time:
 *
 * <ul>
 *   <li>the detailed records — one fingerprint each, which the repetition heuristic compares — are
 *       bounded by <em>both</em> time and count;
 *   <li>the baseline summary — how many calls came before and which tools they used — is bounded by
 *       time alone, since its size is capped by how many tools the server exposes.
 * </ul>
 *
 * <p>When the count limit is reached the oldest records are <em>folded</em> into that summary
 * rather than dropped. Dropping them would erase the baseline exactly when it matters most: an
 * agent enumerating hundreds of tools would blow past the record limit, lose every trace of what it
 * used to do, and then look like a well-behaved newcomer with no history to deviate from.
 */
public final class InMemoryInvocationHistoryAdapter implements InvocationHistoryPort {

  private final Duration retention;
  private final int maxRecordsPerAgent;
  private final Map<String, AgentWindow> windows = new ConcurrentHashMap<>();

  /**
   * @param retention how long anything is kept, records and summary alike; must be positive
   * @param maxRecordsPerAgent cap on detailed records per agent; must be at least 1
   */
  public InMemoryInvocationHistoryAdapter(Duration retention, int maxRecordsPerAgent) {
    Objects.requireNonNull(retention, "retention");
    if (retention.isZero() || retention.isNegative()) {
      throw new IllegalArgumentException("retention must be positive, was " + retention);
    }
    if (maxRecordsPerAgent < 1) {
      throw new IllegalArgumentException(
          "maxRecordsPerAgent must be at least 1, was " + maxRecordsPerAgent);
    }
    this.retention = retention;
    this.maxRecordsPerAgent = maxRecordsPerAgent;
  }

  @Override
  public void record(InvocationRecord invocation) {
    Objects.requireNonNull(invocation, "invocation");
    AgentWindow window =
        windows.computeIfAbsent(invocation.agentId(), unusedKey -> new AgentWindow());
    window.add(invocation, retention, maxRecordsPerAgent);
  }

  @Override
  public AgentHistory historyOf(String agentId, Instant windowStart) {
    Objects.requireNonNull(agentId, "agentId");
    Objects.requireNonNull(windowStart, "windowStart");
    AgentWindow window = windows.get(agentId);
    return window == null ? empty() : window.read(windowStart, retention);
  }

  /** Number of agents currently held, so callers can watch this adapter's footprint. */
  public int trackedAgents() {
    return windows.size();
  }

  /** Forgets everything about every agent. */
  public void clear() {
    windows.clear();
  }

  private static AgentHistory empty() {
    return new AgentHistory(List.of(), Set.of(), 0L);
  }

  /**
   * One agent's window. Every method holds the instance lock: an {@link ArrayDeque} is not safe
   * under concurrent mutation, and the summary must move in step with the records it absorbs.
   */
  private static final class AgentWindow {

    private final Deque<InvocationRecord> records = new ArrayDeque<>();
    private final Set<String> foldedTools = new HashSet<>();
    private long foldedCount;
    private Instant foldedUpTo;

    synchronized void add(InvocationRecord invocation, Duration retention, int maxRecords) {
      records.addLast(invocation);
      expire(invocation.occurredAt(), retention);
      while (records.size() > maxRecords) {
        fold(records.removeFirst());
      }
    }

    synchronized AgentHistory read(Instant windowStart, Duration retention) {
      expire(windowStart, retention);
      List<InvocationRecord> withinWindow = new ArrayList<>();
      Set<String> baselineTools = new HashSet<>(foldedTools);
      long baselineCount = foldedCount;
      for (InvocationRecord invocation : records) {
        if (invocation.occurredAt().isBefore(windowStart)) {
          baselineTools.add(invocation.toolName());
          baselineCount++;
        } else {
          withinWindow.add(invocation);
        }
      }
      return new AgentHistory(withinWindow, baselineTools, baselineCount);
    }

    private void fold(InvocationRecord invocation) {
      foldedCount++;
      foldedTools.add(invocation.toolName());
      if (foldedUpTo == null || foldedUpTo.isBefore(invocation.occurredAt())) {
        foldedUpTo = invocation.occurredAt();
      }
    }

    /**
     * Drops what is older than the retention. Expiry is driven by the latest instant the caller
     * knows about rather than a clock of its own, which keeps the adapter testable and consistent
     * with the instant the chain assigned to the invocation.
     */
    private void expire(Instant now, Duration retention) {
      Instant cutoff = now.minus(retention);
      Iterator<InvocationRecord> oldestFirst = records.iterator();
      while (oldestFirst.hasNext() && oldestFirst.next().occurredAt().isBefore(cutoff)) {
        oldestFirst.remove();
      }
      if (foldedUpTo != null && foldedUpTo.isBefore(cutoff)) {
        foldedTools.clear();
        foldedCount = 0L;
        foldedUpTo = null;
      }
    }
  }
}
