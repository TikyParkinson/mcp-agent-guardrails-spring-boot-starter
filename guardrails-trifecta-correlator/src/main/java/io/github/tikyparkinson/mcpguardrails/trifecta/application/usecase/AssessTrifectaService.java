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
package io.github.tikyparkinson.mcpguardrails.trifecta.application.usecase;

import io.github.tikyparkinson.mcpguardrails.trifecta.application.port.in.AssessTrifectaUseCase;
import io.github.tikyparkinson.mcpguardrails.trifecta.application.port.in.ResetSessionUseCase;
import io.github.tikyparkinson.mcpguardrails.trifecta.application.port.out.SessionCapabilityPort;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.SessionAccumulation;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.SessionCapabilities;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.SessionId;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.TrifectaComplete;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.TrifectaIncomplete;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.TrifectaPolicy;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.TrifectaVerdict;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Tracks which legs of the lethal trifecta a session has touched, and reports when the three meet.
 *
 * <p>Nothing here decides what an undeclared tool does: it contributes no capability and the
 * session is unchanged. Guessing would be worse than not knowing, since the guess would come from
 * data the publisher of the MCP server controls.
 */
public final class AssessTrifectaService implements AssessTrifectaUseCase, ResetSessionUseCase {

  private final SessionCapabilityPort sessionPort;
  private final TrifectaPolicy policy;

  public AssessTrifectaService(SessionCapabilityPort sessionPort, TrifectaPolicy policy) {
    this.sessionPort = Objects.requireNonNull(sessionPort, "sessionPort");
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  @Override
  public TrifectaVerdict assess(SessionId sessionId, String toolName, Instant occurredAt) {
    Objects.requireNonNull(sessionId, "sessionId");
    Objects.requireNonNull(toolName, "toolName");
    Objects.requireNonNull(occurredAt, "occurredAt");
    SessionAccumulation accumulation =
        sessionPort.accumulate(sessionId, policy.capabilitiesOf(toolName), occurredAt);
    SessionCapabilities session = accumulation.session();
    if (!session.hasTrifecta()) {
      return new TrifectaIncomplete(session.capabilities());
    }
    return new TrifectaComplete(session.capabilities(), accumulation.closedNow());
  }

  @Override
  public List<SessionId> lockedSessions() {
    return sessionPort.withTrifecta();
  }

  @Override
  public boolean reset(SessionId sessionId) {
    Objects.requireNonNull(sessionId, "sessionId");
    return sessionPort.forget(sessionId);
  }
}
