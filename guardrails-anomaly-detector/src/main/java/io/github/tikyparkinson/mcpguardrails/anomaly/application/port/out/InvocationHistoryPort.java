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
package io.github.tikyparkinson.mcpguardrails.anomaly.application.port.out;

import io.github.tikyparkinson.mcpguardrails.anomaly.domain.AgentHistory;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.InvocationRecord;
import java.time.Instant;

/**
 * Outbound port for the invocation history. Replace it to read the history from somewhere else —
 * the starter can bridge it onto the audit log, for one.
 *
 * <p>The port is declared here, in this module's own vocabulary, rather than borrowed from {@code
 * guardrails-audit}: no guardrail depends on another (ARCHITECTURE.md §5), and {@link AgentHistory}
 * answers exactly the question the heuristics ask, nothing more.
 */
public interface InvocationHistoryPort {

  /** Adds the invocation to the history. Must be safe under concurrency. */
  void record(InvocationRecord record);

  /**
   * The agent's history split at {@code windowStart}: what happened from that instant onwards
   * (inclusive) and the baseline before it. Never null.
   */
  AgentHistory historyOf(String agentId, Instant windowStart);
}
