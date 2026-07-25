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

/**
 * Pure combination rule for guardrail decisions.
 *
 * <p>Severity order is {@code Deny > Escalate > Allow}. Among equal severity the first decision in
 * evaluation order wins. An empty list combines to {@link Allow}.
 */
public final class DecisionCombiner {

  private DecisionCombiner() {}

  /** Combines the given evaluations into the final decision of the chain. */
  public static GuardrailDecision combine(List<GuardrailEvaluation> evaluations) {
    Objects.requireNonNull(evaluations, "evaluations");
    Optional<GuardrailDecision> firstDeny = firstOfType(evaluations, Deny.class);
    return firstDeny.orElseGet(
        () -> firstOfType(evaluations, Escalate.class).orElseGet(Allow::new));
  }

  private static Optional<GuardrailDecision> firstOfType(
      List<GuardrailEvaluation> evaluations, Class<? extends GuardrailDecision> type) {
    return evaluations.stream()
        .map(GuardrailEvaluation::decision)
        .filter(type::isInstance)
        .findFirst();
  }
}
