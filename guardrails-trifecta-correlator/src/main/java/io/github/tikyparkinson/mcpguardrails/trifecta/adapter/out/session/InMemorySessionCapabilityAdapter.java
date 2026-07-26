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

import io.github.tikyparkinson.mcpguardrails.trifecta.application.port.out.SessionCapabilityPort;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.Capability;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.SessionAccumulation;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.SessionCapabilities;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.SessionId;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default session store, held in this process.
 *
 * <p>Sessions expire two ways, and both are needed. The idle bound closes the session of an agent
 * that stopped working. The absolute bound closes the session of one that never stops: every
 * invocation refreshes the idle clock, so a busy agent would never reach it — measured at 8640
 * invocations over 24 hours without a single expiry — and a trifecta closed in the morning would
 * still be escalating the next day.
 *
 * <p>Sessions live only here: a restart loses what was accumulated, and behind a load balancer each
 * replica sees only its own share, so an agent spread across replicas can close the triangle
 * without any single replica seeing it whole. Implement {@link SessionCapabilityPort} against a
 * shared store to fix that.
 */
public final class InMemorySessionCapabilityAdapter implements SessionCapabilityPort {

  private final Duration idleTimeout;
  private final Duration maxDuration;
  private final Map<SessionId, SessionCapabilities> sessions = new ConcurrentHashMap<>();

  /**
   * @param idleTimeout how long without invocations before a session is forgotten; must be positive
   * @param maxDuration how long a session may live from its first invocation; must be positive and
   *     no shorter than {@code idleTimeout}
   */
  public InMemorySessionCapabilityAdapter(Duration idleTimeout, Duration maxDuration) {
    requirePositive(idleTimeout, "idleTimeout");
    requirePositive(maxDuration, "maxDuration");
    if (maxDuration.compareTo(idleTimeout) < 0) {
      throw new IllegalArgumentException(
          "maxDuration (%s) must not be shorter than idleTimeout (%s)"
              .formatted(maxDuration, idleTimeout));
    }
    this.idleTimeout = idleTimeout;
    this.maxDuration = maxDuration;
  }

  /**
   * Merges and reads inside a single {@code compute}, which {@link ConcurrentHashMap} runs
   * atomically for the key. Reading the session and then writing it back would let two concurrent
   * invocations lose one contribution, and both report having closed the triangle.
   */
  @Override
  public SessionAccumulation accumulate(
      SessionId sessionId, Set<Capability> capabilities, Instant occurredAt) {
    Objects.requireNonNull(sessionId, "sessionId");
    Objects.requireNonNull(capabilities, "capabilities");
    Objects.requireNonNull(occurredAt, "occurredAt");
    AtomicBoolean completeBefore = new AtomicBoolean();
    SessionCapabilities merged =
        sessions.compute(
            sessionId,
            (unused, current) -> {
              SessionCapabilities alive = expired(current, occurredAt) ? null : current;
              completeBefore.set(alive != null && alive.hasTrifecta());
              return alive == null
                  ? SessionCapabilities.starting(capabilities, occurredAt)
                  : alive.plus(capabilities, occurredAt);
            });
    return new SessionAccumulation(merged, completeBefore.get());
  }

  @Override
  public List<SessionId> withTrifecta() {
    return sessions.entrySet().stream()
        .filter(entry -> entry.getValue().hasTrifecta())
        .sorted(Comparator.comparing(entry -> entry.getValue().startedAt()))
        .map(Map.Entry::getKey)
        .toList();
  }

  @Override
  public boolean forget(SessionId sessionId) {
    Objects.requireNonNull(sessionId, "sessionId");
    return sessions.remove(sessionId) != null;
  }

  /** Number of sessions currently held, so callers can watch this adapter's footprint. */
  public int trackedSessions() {
    return sessions.size();
  }

  /** Forgets every session. */
  public void clear() {
    sessions.clear();
  }

  /**
   * A session is gone when it has been idle for too long or has simply lived too long. Expiry is
   * measured against the instant the invocation carries rather than a clock of its own, which keeps
   * the adapter testable and consistent with the rest of the chain.
   */
  private boolean expired(SessionCapabilities session, Instant now) {
    if (session == null) {
      return true;
    }
    return session.lastSeenAt().isBefore(now.minus(idleTimeout))
        || session.startedAt().isBefore(now.minus(maxDuration));
  }

  private static void requirePositive(Duration value, String field) {
    Objects.requireNonNull(value, field);
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(field + " must be positive, was " + value);
    }
  }
}
