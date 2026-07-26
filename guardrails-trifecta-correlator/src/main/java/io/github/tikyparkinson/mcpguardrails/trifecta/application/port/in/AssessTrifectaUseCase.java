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
package io.github.tikyparkinson.mcpguardrails.trifecta.application.port.in;

import io.github.tikyparkinson.mcpguardrails.trifecta.domain.SessionId;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.TrifectaVerdict;
import java.time.Instant;

/** Inbound port used by the chain guardrail on every invocation. */
public interface AssessTrifectaUseCase {

  /**
   * Adds what this invocation contributes to the session and says whether the three legs now meet.
   * Never returns null.
   *
   * <p>Takes an already-derived {@link SessionId} rather than an agent: what counts as a session is
   * decided by the inbound adapter, the only place that sees the whole invocation context.
   */
  TrifectaVerdict assess(SessionId sessionId, String toolName, Instant occurredAt);
}
