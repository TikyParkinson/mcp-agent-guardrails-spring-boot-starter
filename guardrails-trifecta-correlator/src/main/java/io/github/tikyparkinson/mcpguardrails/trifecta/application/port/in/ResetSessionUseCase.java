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
import java.util.List;

/**
 * Inbound port for the human side. Whatever the operator exposes — the same channel that resolves
 * approvals — talks to this.
 */
public interface ResetSessionUseCase {

  /** Sessions whose trifecta is closed right now, oldest first. Never null. */
  List<SessionId> lockedSessions();

  /**
   * Forgets what a session accumulated. False when there was nothing to forget.
   *
   * <p>Without this, a session with a closed trifecta escalates every invocation until it expires,
   * and approval-gate's quotas jam the agent. Reopening a session is a deliberate decision by a
   * person, not something that happens on its own.
   */
  boolean reset(SessionId sessionId);
}
