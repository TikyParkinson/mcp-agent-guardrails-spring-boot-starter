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
import java.util.regex.Pattern;

/**
 * Draft of an audit event as provided by the caller; {@code eventId} and {@code occurredAt} are
 * assigned by the use case when the event is recorded.
 *
 * <p>Every text field is stripped of control characters before it is stored. Agents choose the
 * names of their own arguments and, with the default resolver, their own identifier, and those
 * values reach the trail inside decision reasons. A newline there would let an agent forge a
 * complete audit entry once the trail is written to a plain-text log — attacking the record is
 * attacking exactly what a governance product is for.
 *
 * <p>The values are sanitized rather than rejected, and that is deliberate. Publishing happens
 * inside decorators that swallow their own failures so a broken audit store never blocks a call, so
 * rejecting here would hand an agent a way to <em>delete</em> its own trace by putting a newline in
 * an argument name. Losing the entry is worse than logging it with a space in it.
 */
public record NewAuditEvent(
    String agentId, String toolName, String emittedBy, AuditEventType type, String detail) {

  /** Control and format characters, including the bidi overrides used to disguise text. */
  private static final Pattern UNPRINTABLE = Pattern.compile("[\\p{Cc}\\p{Cf}]");

  public NewAuditEvent {
    Objects.requireNonNull(type, "type");
    detail = sanitize(Objects.requireNonNull(detail, "detail"));
    agentId = requireNotBlank(agentId, "agentId");
    toolName = requireNotBlank(toolName, "toolName");
    emittedBy = requireNotBlank(emittedBy, "emittedBy");
  }

  private static String requireNotBlank(String value, String field) {
    Objects.requireNonNull(value, field);
    String sanitized = sanitize(value);
    if (sanitized.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return sanitized;
  }

  /** Replaces anything unprintable with a space, so the value stays readable and stays one line. */
  private static String sanitize(String value) {
    return UNPRINTABLE.matcher(value).replaceAll(" ");
  }
}
