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
package io.github.tikyparkinson.mcpguardrails.anomaly.adapter.in.chain;

import io.github.tikyparkinson.mcpguardrails.anomaly.application.port.in.DetectAnomalyUseCase;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.AnomalyDetected;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.AnomalySignal;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.NoAnomaly;
import io.github.tikyparkinson.mcpguardrails.core.application.port.out.Guardrail;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Anomaly guardrail: an agent whose recent behaviour looks like a loop, or like a sudden sweep
 * across tools it never used, is escalated to a human.
 *
 * <p>Runs at order 80, after {@code egress-control} (70) and before {@code ratelimit} (100).
 *
 * <p>The outcome is only ever {@link Allow} or {@link Escalate}, never {@code Deny}. These are
 * threshold heuristics: a legitimate retry with backoff reaches the repetition threshold just as a
 * runaway loop does, and blocking on that would produce incidents nobody can reproduce. Escalating
 * puts a human in front of the same evidence instead.
 */
public final class AnomalyGuardrail implements Guardrail {

  public static final String GUARDRAIL_NAME = "anomaly-detector";

  private final DetectAnomalyUseCase useCase;

  public AnomalyGuardrail(DetectAnomalyUseCase useCase) {
    this.useCase = Objects.requireNonNull(useCase, "useCase");
  }

  @Override
  public String name() {
    return GUARDRAIL_NAME;
  }

  @Override
  public int order() {
    return 80;
  }

  /**
   * The invocation instant comes from the context, not from a clock read here: the chain already
   * decided when this call happened, and reading a second clock would make the window depend on how
   * long the guardrails before this one took.
   */
  @Override
  public GuardrailDecision evaluate(ToolInvocationContext context) {
    Objects.requireNonNull(context, "context");
    return switch (useCase.inspect(
        context.agentId().value(),
        context.toolName().value(),
        context.arguments(),
        context.occurredAt())) {
      case NoAnomaly _ -> new Allow();
      case AnomalyDetected detected -> new Escalate(describe(detected));
    };
  }

  private static String describe(AnomalyDetected detected) {
    String signals =
        detected.signals().stream().map(AnomalySignal::describe).collect(Collectors.joining("; "));
    return "anomalous agent behaviour (" + signals + ")";
  }
}
