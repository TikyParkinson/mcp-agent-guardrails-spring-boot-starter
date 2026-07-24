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

import io.github.tikyparkinson.mcpguardrails.audit.application.port.in.RecordAuditEventUseCase;
import io.github.tikyparkinson.mcpguardrails.audit.application.port.out.AuditLogStorePort;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEvent;
import io.github.tikyparkinson.mcpguardrails.audit.domain.NewAuditEvent;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Completes audit event drafts and appends them to the audit log store. Store failures propagate to
 * the caller (fail-closed by design).
 */
public final class RecordAuditEventService implements RecordAuditEventUseCase {

  private final AuditLogStorePort store;
  private final Clock clock;
  private final Supplier<UUID> idGenerator;

  public RecordAuditEventService(AuditLogStorePort store, Clock clock, Supplier<UUID> idGenerator) {
    this.store = Objects.requireNonNull(store, "store");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
  }

  @Override
  public AuditEvent publish(NewAuditEvent draft) {
    Objects.requireNonNull(draft, "draft");
    AuditEvent event =
        new AuditEvent(
            idGenerator.get(),
            draft.agentId(),
            draft.toolName(),
            clock.instant(),
            draft.emittedBy(),
            draft.type(),
            draft.detail());
    store.append(event);
    return event;
  }
}
