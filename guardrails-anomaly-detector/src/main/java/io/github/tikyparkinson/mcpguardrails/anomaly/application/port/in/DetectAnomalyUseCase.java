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
package io.github.tikyparkinson.mcpguardrails.anomaly.application.port.in;

import io.github.tikyparkinson.mcpguardrails.anomaly.domain.AnomalyVerdict;
import java.time.Instant;
import java.util.Map;

/** Inbound port used by the chain guardrail to check how an agent is behaving. */
public interface DetectAnomalyUseCase {

  /**
   * Records the invocation in the history and analyses the agent over the current window. Never
   * returns null.
   */
  AnomalyVerdict inspect(
      String agentId, String toolName, Map<String, Object> arguments, Instant occurredAt);
}
