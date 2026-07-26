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
package io.github.tikyparkinson.mcpguardrails.anomaly.application.usecase;

import io.github.tikyparkinson.mcpguardrails.anomaly.application.port.in.DetectAnomalyUseCase;
import io.github.tikyparkinson.mcpguardrails.anomaly.application.port.out.InvocationHistoryPort;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.AgentHistory;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.AnomalyAnalyzer;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.AnomalyPolicy;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.AnomalyVerdict;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.ArgumentsFingerprint;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.InvocationRecord;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Records the invocation and analyses the agent's recent behaviour against it.
 *
 * <p>The invocation in flight is part of the window it is analysed against, so a loop is cut at the
 * call that reaches the threshold rather than one call later.
 */
public final class DetectAnomalyService implements DetectAnomalyUseCase {

  private final InvocationHistoryPort historyPort;
  private final AnomalyPolicy policy;

  public DetectAnomalyService(InvocationHistoryPort historyPort, AnomalyPolicy policy) {
    this.historyPort = Objects.requireNonNull(historyPort, "historyPort");
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  @Override
  public AnomalyVerdict inspect(
      String agentId, String toolName, Map<String, Object> arguments, Instant occurredAt) {
    Objects.requireNonNull(agentId, "agentId");
    Objects.requireNonNull(toolName, "toolName");
    Objects.requireNonNull(arguments, "arguments");
    Objects.requireNonNull(occurredAt, "occurredAt");
    ArgumentsFingerprint fingerprint = ArgumentsFingerprint.of(arguments);
    historyPort.record(new InvocationRecord(agentId, toolName, fingerprint, occurredAt));
    AgentHistory history = historyPort.historyOf(agentId, occurredAt.minus(policy.window()));
    return AnomalyAnalyzer.analyze(history, policy);
  }
}
