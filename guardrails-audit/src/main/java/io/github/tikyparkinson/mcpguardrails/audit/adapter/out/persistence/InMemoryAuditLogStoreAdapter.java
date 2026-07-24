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
package io.github.tikyparkinson.mcpguardrails.audit.adapter.out.persistence;

import io.github.tikyparkinson.mcpguardrails.audit.application.port.out.AuditLogStorePort;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Default in-memory audit log store: a bounded, thread-safe buffer that evicts the oldest event
 * once {@code maxEvents} is exceeded. Intended for development and as the zero-configuration
 * default; replace it by exposing your own {@link AuditLogStorePort} bean.
 */
public final class InMemoryAuditLogStoreAdapter implements AuditLogStorePort {

  private final Deque<AuditEvent> events = new ArrayDeque<>();
  private final int maxEvents;

  public InMemoryAuditLogStoreAdapter(int maxEvents) {
    if (maxEvents < 1) {
      throw new IllegalArgumentException("maxEvents must be >= 1, got " + maxEvents);
    }
    this.maxEvents = maxEvents;
  }

  @Override
  public synchronized void append(AuditEvent event) {
    Objects.requireNonNull(event, "event");
    if (events.size() == maxEvents) {
      events.removeFirst();
    }
    events.addLast(event);
  }

  @Override
  public synchronized List<AuditEvent> findRecent(int limit) {
    requirePositive(limit);
    List<AuditEvent> recent = new ArrayList<>(Math.min(limit, events.size()));
    var it = events.descendingIterator();
    while (it.hasNext() && recent.size() < limit) {
      recent.add(it.next());
    }
    return List.copyOf(recent);
  }

  private static void requirePositive(int limit) {
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be >= 1, got " + limit);
    }
  }
}
