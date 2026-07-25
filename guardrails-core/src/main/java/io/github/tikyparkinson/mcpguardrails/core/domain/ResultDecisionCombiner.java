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
package io.github.tikyparkinson.mcpguardrails.core.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Pure combination rule for outbound guardrail decisions.
 *
 * <p>Severity order is {@code Block > Redact > PassThrough}. The first {@code Block} in evaluation
 * order wins. When nobody blocks but somebody redacted, the combined decision carries the contents
 * accumulated by the chain and the reasons of every redaction.
 */
public final class ResultDecisionCombiner {

  private ResultDecisionCombiner() {}

  /**
   * Combines the given evaluations into the final decision of the outbound chain.
   *
   * @param accumulated textual contents after applying every redaction in cascade
   */
  public static ResultDecision combine(
      List<ResultEvaluation> evaluations, List<String> accumulated) {
    Objects.requireNonNull(evaluations, "evaluations");
    Objects.requireNonNull(accumulated, "accumulated");
    return firstBlock(evaluations)
        .orElseGet(() -> redactionOrPassThrough(evaluations, accumulated));
  }

  private static Optional<ResultDecision> firstBlock(List<ResultEvaluation> evaluations) {
    return evaluations.stream()
        .map(ResultEvaluation::decision)
        .filter(Block.class::isInstance)
        .findFirst();
  }

  private static ResultDecision redactionOrPassThrough(
      List<ResultEvaluation> evaluations, List<String> accumulated) {
    String reasons =
        evaluations.stream()
            .map(ResultEvaluation::decision)
            .filter(Redact.class::isInstance)
            .map(decision -> ((Redact) decision).reason())
            .collect(Collectors.joining("; "));
    return reasons.isEmpty() ? new PassThrough() : new Redact(accumulated, reasons);
  }
}
