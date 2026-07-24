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
package io.github.tikyparkinson.mcpguardrails.audit.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.tikyparkinson.mcpguardrails.audit.application.port.out.AuditLogStorePort;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEvent;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEventType;
import io.github.tikyparkinson.mcpguardrails.audit.domain.NewAuditEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecordAuditEventServiceTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-07-24T10:00:00Z");
  private static final UUID FIXED_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");
  private static final NewAuditEvent DRAFT =
      new NewAuditEvent("agent-1", "search", "audit", AuditEventType.TOOL_INVOKED, "");

  private final AuditLogStorePort store = mock(AuditLogStorePort.class);
  private final RecordAuditEventService service =
      new RecordAuditEventService(store, Clock.fixed(FIXED_NOW, ZoneOffset.UTC), () -> FIXED_ID);

  @Test
  void shouldCompleteDraftAndPersistWhenRecording() {
    // given / when
    AuditEvent recorded = service.record(DRAFT);

    // then
    AuditEvent expected =
        new AuditEvent(
            FIXED_ID, "agent-1", "search", FIXED_NOW, "audit", AuditEventType.TOOL_INVOKED, "");
    assertEquals(expected, recorded);
    verify(store).append(expected);
  }

  @Test
  void shouldPropagateStoreFailureWhenAppendThrows() {
    // given: fail-closed contract — store errors must not be swallowed
    doThrow(new IllegalStateException("store down")).when(store).append(any());

    // when / then
    assertThrows(IllegalStateException.class, () -> service.record(DRAFT));
  }

  @Test
  void shouldRejectNullDraftWhenRecording() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> service.record(null));
  }

  @Test
  void shouldRejectNullCollaboratorsWhenConstructed() {
    // given
    Clock clock = Clock.systemUTC();

    // when / then
    assertThrows(
        NullPointerException.class,
        () -> new RecordAuditEventService(null, clock, UUID::randomUUID));
    assertThrows(
        NullPointerException.class,
        () -> new RecordAuditEventService(store, null, UUID::randomUUID));
    assertThrows(NullPointerException.class, () -> new RecordAuditEventService(store, clock, null));
  }
}
