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
package io.github.tikyparkinson.mcpguardrails.audit.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventTest {

  private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");

  @Test
  void shouldRejectNullFieldsWhenConstructed() {
    // given / when / then
    assertThrows(
        NullPointerException.class,
        () -> new AuditEvent(null, "a", "t", NOW, "audit", AuditEventType.TOOL_INVOKED, ""));
    assertThrows(
        NullPointerException.class,
        () -> new AuditEvent(ID, "a", "t", null, "audit", AuditEventType.TOOL_INVOKED, ""));
    assertThrows(
        NullPointerException.class, () -> new AuditEvent(ID, "a", "t", NOW, "audit", null, ""));
    assertThrows(
        NullPointerException.class,
        () -> new AuditEvent(ID, "a", "t", NOW, "audit", AuditEventType.TOOL_INVOKED, null));
  }

  @Test
  void shouldRejectBlankIdentityFieldsWhenConstructed() {
    // given / when / then
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditEvent(ID, " ", "t", NOW, "audit", AuditEventType.TOOL_INVOKED, ""));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditEvent(ID, "a", "", NOW, "audit", AuditEventType.TOOL_INVOKED, ""));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuditEvent(ID, "a", "t", NOW, " ", AuditEventType.TOOL_INVOKED, ""));
  }

  @Test
  void shouldRejectInvalidDraftWhenConstructed() {
    // given / when / then
    assertThrows(
        IllegalArgumentException.class,
        () -> new NewAuditEvent(" ", "t", "audit", AuditEventType.TOOL_INVOKED, ""));
    assertThrows(
        IllegalArgumentException.class,
        () -> new NewAuditEvent("a", "", "audit", AuditEventType.TOOL_INVOKED, ""));
    assertThrows(
        IllegalArgumentException.class,
        () -> new NewAuditEvent("a", "t", " ", AuditEventType.TOOL_INVOKED, ""));
    assertThrows(NullPointerException.class, () -> new NewAuditEvent("a", "t", "audit", null, ""));
    assertThrows(
        NullPointerException.class,
        () -> new NewAuditEvent("a", "t", "audit", AuditEventType.TOOL_INVOKED, null));
  }
}
