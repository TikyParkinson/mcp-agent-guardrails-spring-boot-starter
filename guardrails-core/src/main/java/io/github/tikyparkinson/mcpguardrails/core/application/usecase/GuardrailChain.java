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
package io.github.tikyparkinson.mcpguardrails.core.application.usecase;

import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolInvocationUseCase;
import io.github.tikyparkinson.mcpguardrails.core.application.port.out.Guardrail;
import io.github.tikyparkinson.mcpguardrails.core.domain.ChainVerdict;
import io.github.tikyparkinson.mcpguardrails.core.domain.DecisionCombiner;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailEvaluation;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Evaluates every registered {@link Guardrail} in deterministic order and combines their decisions.
 *
 * <p>All guardrails are always evaluated (no short-circuit) so the verdict carries the full trace.
 * An unexpected exception from a guardrail is recorded as a {@code Deny} (fail-closed).
 */
public final class GuardrailChain implements EvaluateToolInvocationUseCase {

  private final List<Guardrail> guardrails;

  public GuardrailChain(List<Guardrail> guardrails) {
    Objects.requireNonNull(guardrails, "guardrails");
    this.guardrails = sortedUniqueCopy(guardrails);
  }

  @Override
  public ChainVerdict evaluate(ToolInvocationContext context) {
    Objects.requireNonNull(context, "context");
    List<GuardrailEvaluation> evaluations = new ArrayList<>(guardrails.size());
    for (Guardrail guardrail : guardrails) {
      evaluations.add(new GuardrailEvaluation(guardrail.name(), safeEvaluate(guardrail, context)));
    }
    return new ChainVerdict(DecisionCombiner.combine(evaluations), evaluations);
  }

  private static GuardrailDecision safeEvaluate(Guardrail guardrail, ToolInvocationContext ctx) {
    try {
      return Objects.requireNonNull(
          guardrail.evaluate(ctx), () -> "guardrail " + guardrail.name() + " returned null");
    } catch (RuntimeException e) {
      return new Deny("guardrail " + guardrail.name() + " failed: " + e.getClass().getSimpleName());
    }
  }

  private static List<Guardrail> sortedUniqueCopy(List<Guardrail> guardrails) {
    Set<String> names = new HashSet<>();
    for (Guardrail guardrail : guardrails) {
      if (!names.add(guardrail.name())) {
        throw new IllegalArgumentException("Duplicate guardrail name: " + guardrail.name());
      }
    }
    return guardrails.stream()
        .sorted(Comparator.comparingInt(Guardrail::order).thenComparing(Guardrail::name))
        .toList();
  }
}
