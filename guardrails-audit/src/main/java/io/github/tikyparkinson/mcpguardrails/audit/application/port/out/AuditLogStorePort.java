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
package io.github.tikyparkinson.mcpguardrails.audit.application.port.out;

import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEvent;
import java.util.List;

/**
 * Outbound port for the audit log store. Implementations must not swallow failures: a broken store
 * surfaces as a RuntimeException so the chain can fail closed.
 */
public interface AuditLogStorePort {

  /** Persists the event. Throws a RuntimeException if the store fails. */
  void append(AuditEvent event);

  /** Returns the last {@code limit} events, most recent first. {@code limit >= 1}. Never null. */
  List<AuditEvent> findRecent(int limit);
}
