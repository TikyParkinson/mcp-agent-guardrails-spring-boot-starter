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
package io.github.tikyparkinson.mcpguardrails.anomaly.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The two heuristics, deterministic and explainable by hand.
 *
 * <p>They are independent: both can fire on the same history, and both signals travel in the
 * verdict.
 */
public final class AnomalyAnalyzer {

  private AnomalyAnalyzer() {}

  /** Analyses the agent against the thresholds. Never returns null. */
  public static AnomalyVerdict analyze(AgentHistory history, AnomalyPolicy policy) {
    Objects.requireNonNull(history, "history");
    Objects.requireNonNull(policy, "policy");
    List<AnomalySignal> signals = new ArrayList<>(2);
    repetitionLoop(history, policy).ifPresent(signals::add);
    novelToolBurst(history, policy).ifPresent(signals::add);
    return signals.isEmpty() ? new NoAnomaly() : new AnomalyDetected(signals);
  }

  /**
   * Identical calls — same tool, same arguments — repeated beyond the threshold. Records without a
   * known fingerprint are skipped: they carry no evidence of being identical to anything.
   */
  private static Optional<AnomalySignal> repetitionLoop(
      AgentHistory history, AnomalyPolicy policy) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    Map<String, String> toolOfGroup = new LinkedHashMap<>();
    for (InvocationRecord invocation : history.withinWindow()) {
      if (!invocation.fingerprint().isKnown()) {
        continue;
      }
      String key = invocation.toolName() + '\u0000' + invocation.fingerprint().value();
      counts.merge(key, 1, Integer::sum);
      toolOfGroup.putIfAbsent(key, invocation.toolName());
    }
    return counts.entrySet().stream()
        .max(Map.Entry.comparingByValue())
        .filter(largest -> largest.getValue() >= policy.repeatThreshold())
        .map(
            largest ->
                new AnomalySignal(
                    AnomalyKind.REPETITION_LOOP,
                    largest.getValue(),
                    policy.repeatThreshold(),
                    toolOfGroup.get(largest.getKey())));
  }

  /**
   * Tools the agent had never used before appearing together inside the window. Stays quiet until
   * the baseline is long enough: without one, every tool is new and the heuristic would lie.
   */
  private static Optional<AnomalySignal> novelToolBurst(
      AgentHistory history, AnomalyPolicy policy) {
    if (history.invocationsBeforeWindow() < policy.baselineMinInvocations()) {
      return Optional.empty();
    }
    List<String> novel =
        history.withinWindow().stream()
            .map(InvocationRecord::toolName)
            .filter(tool -> !history.toolsBeforeWindow().contains(tool))
            .distinct()
            .sorted()
            .toList();
    if (novel.size() < policy.novelToolThreshold()) {
      return Optional.empty();
    }
    return Optional.of(
        new AnomalySignal(
            AnomalyKind.NOVEL_TOOL_BURST,
            novel.size(),
            policy.novelToolThreshold(),
            String.join(", ", novel)));
  }
}
