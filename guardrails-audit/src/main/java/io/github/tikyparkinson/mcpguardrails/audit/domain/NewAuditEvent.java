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

import java.util.Objects;

/**
 * Draft of an audit event as provided by the caller; {@code eventId} and {@code occurredAt} are
 * assigned by the use case when the event is recorded.
 */
public record NewAuditEvent(
    String agentId, String toolName, String emittedBy, AuditEventType type, String detail) {

  public NewAuditEvent {
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
