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

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable audit event as persisted in the audit log.
 *
 * <p>Tool arguments are deliberately not part of the event (PII/secret risk); {@code detail}
 * carries a short, emitter-controlled text and may be empty.
 */
public record AuditEvent(
    UUID eventId,
    String agentId,
    String toolName,
    Instant occurredAt,
    String emittedBy,
    AuditEventType type,
    String detail) {

  public AuditEvent {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(detail, "detail");
    requireNotBlank(agentId, "agentId");
    requireNotBlank(toolName, "toolName");
    requireNotBlank(emittedBy, "emittedBy");
  }

  private static void requireNotBlank(String value, String field) {
    Objects.requireNonNull(value, field);
    if (value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
