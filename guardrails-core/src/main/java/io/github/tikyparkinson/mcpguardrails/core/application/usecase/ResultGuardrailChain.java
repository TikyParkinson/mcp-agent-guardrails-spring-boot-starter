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

import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolResultUseCase;
import io.github.tikyparkinson.mcpguardrails.core.application.port.out.ResultGuardrail;
import io.github.tikyparkinson.mcpguardrails.core.domain.Block;
import io.github.tikyparkinson.mcpguardrails.core.domain.Redact;
import io.github.tikyparkinson.mcpguardrails.core.domain.ResultDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ResultDecisionCombiner;
import io.github.tikyparkinson.mcpguardrails.core.domain.ResultEvaluation;
import io.github.tikyparkinson.mcpguardrails.core.domain.ResultVerdict;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolResultContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Evaluates every registered {@link ResultGuardrail} in deterministic order and combines their
 * decisions.
 *
 * <p>All guardrails are always evaluated (no short-circuit) so the verdict carries the full trace,
 * and redactions compose in cascade: each guardrail sees what the previous ones already sanitized.
 * A guardrail that fails, returns null, or breaks the positional contract of {@link Redact} is
 * recorded as a {@code Block} (fail-closed).
 *
 * <p>With no registered guardrails the verdict is {@code PassThrough}, leaving the result
 * untouched.
 */
public final class ResultGuardrailChain implements EvaluateToolResultUseCase {

  private final List<ResultGuardrail> guardrails;

  public ResultGuardrailChain(List<ResultGuardrail> guardrails) {
    Objects.requireNonNull(guardrails, "guardrails");
    this.guardrails = sortedUniqueCopy(guardrails);
  }

  @Override
  public ResultVerdict evaluate(ToolResultContext context) {
    Objects.requireNonNull(context, "context");
    List<ResultEvaluation> evaluations = new ArrayList<>(guardrails.size());
    ToolResultContext current = context;
    for (ResultGuardrail guardrail : guardrails) {
      Step step = inspect(guardrail, current);
      evaluations.add(new ResultEvaluation(guardrail.name(), step.decision()));
      current = step.context();
    }
    ResultDecision finalDecision =
        ResultDecisionCombiner.combine(evaluations, current.textContents());
    return new ResultVerdict(finalDecision, evaluations);
  }

  private static Step inspect(ResultGuardrail guardrail, ToolResultContext current) {
    ResultDecision decision = safeInspect(guardrail, current);
    if (!(decision instanceof Redact redact)) {
      return new Step(decision, current);
    }
    int expected = current.textContents().size();
    int actual = redact.sanitizedContents().size();
    if (actual != expected) {
      return new Step(
          new Block(
              "outbound guardrail %s returned %d contents, expected %d"
                  .formatted(guardrail.name(), actual, expected)),
          current);
    }
    return new Step(decision, current.withTextContents(redact.sanitizedContents()));
  }

  private static ResultDecision safeInspect(ResultGuardrail guardrail, ToolResultContext current) {
    try {
      ResultDecision decision = guardrail.inspect(current);
      return decision == null
          ? new Block("outbound guardrail " + guardrail.name() + " returned null")
          : decision;
    } catch (RuntimeException e) {
      return new Block(
          "outbound guardrail " + guardrail.name() + " failed: " + e.getClass().getSimpleName());
    }
  }

  private static List<ResultGuardrail> sortedUniqueCopy(List<ResultGuardrail> guardrails) {
    Set<String> names = new HashSet<>();
    for (ResultGuardrail guardrail : guardrails) {
      if (!names.add(guardrail.name())) {
        throw new IllegalArgumentException(
            "Duplicate outbound guardrail name: " + guardrail.name());
      }
    }
    return guardrails.stream()
        .sorted(
            Comparator.comparingInt(ResultGuardrail::order).thenComparing(ResultGuardrail::name))
        .toList();
  }

  /** Decision of one guardrail plus the result state after applying it. */
  private record Step(ResultDecision decision, ToolResultContext context) {}
}
