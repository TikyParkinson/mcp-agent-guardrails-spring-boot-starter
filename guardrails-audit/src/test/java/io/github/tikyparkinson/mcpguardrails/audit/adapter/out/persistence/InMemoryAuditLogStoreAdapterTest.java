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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEvent;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEventType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryAuditLogStoreAdapterTest {

  @Test
  void shouldReturnMostRecentFirstWhenFindingRecent() {
    // given
    InMemoryAuditLogStoreAdapter store = new InMemoryAuditLogStoreAdapter(10);
    store.append(event(1));
    store.append(event(2));
    store.append(event(3));

    // when
    List<AuditEvent> recent = store.findRecent(2);

    // then
    assertEquals(List.of(event(3), event(2)), recent);
  }

  @Test
  void shouldEvictOldestWhenCapacityExceeded() {
    // given
    InMemoryAuditLogStoreAdapter store = new InMemoryAuditLogStoreAdapter(2);
    store.append(event(1));
    store.append(event(2));

    // when
    store.append(event(3));

    // then
    assertEquals(List.of(event(3), event(2)), store.findRecent(10));
  }

  @Test
  void shouldReturnEmptyListWhenStoreIsEmpty() {
    // given
    InMemoryAuditLogStoreAdapter store = new InMemoryAuditLogStoreAdapter(5);

    // when / then
    assertEquals(List.of(), store.findRecent(1));
  }

  @Test
  void shouldRejectInvalidLimitWhenFindingRecent() {
    // given
    InMemoryAuditLogStoreAdapter store = new InMemoryAuditLogStoreAdapter(5);

    // when / then
    assertThrows(IllegalArgumentException.class, () -> store.findRecent(0));
  }

  @Test
  void shouldRejectInvalidCapacityWhenConstructed() {
    // given / when / then
    assertThrows(IllegalArgumentException.class, () -> new InMemoryAuditLogStoreAdapter(0));
  }

  @Test
  void shouldRejectNullEventWhenAppending() {
    // given
    InMemoryAuditLogStoreAdapter store = new InMemoryAuditLogStoreAdapter(5);

    // when / then
    assertThrows(NullPointerException.class, () -> store.append(null));
  }

  private static AuditEvent event(int seq) {
    return new AuditEvent(
        new UUID(0, seq),
        "agent-1",
        "search",
        Instant.parse("2026-07-24T10:00:00Z").plusSeconds(seq),
        "audit",
        AuditEventType.TOOL_INVOKED,
        "");
  }
}
