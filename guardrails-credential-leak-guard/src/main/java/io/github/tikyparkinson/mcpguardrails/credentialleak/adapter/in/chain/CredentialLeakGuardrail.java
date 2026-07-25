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
package io.github.tikyparkinson.mcpguardrails.credentialleak.adapter.in.chain;

import io.github.tikyparkinson.mcpguardrails.core.application.port.out.Guardrail;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.in.ScanToolArgumentsForSecretsUseCase;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretFinding;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretScanResult;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretSeverity;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Inbound guardrail: stops an invocation that carries a credential in its arguments.
 *
 * <p>Arguments are never rewritten — a tool receiving data other than what the agent asked for,
 * invisibly to both, is worse than a stopped call. Reasons list {@code patternId@location} pairs
 * and never the detected value: that message reaches the model.
 *
 * <p>Runs at order 60, right after {@code injection-guard}: both inspect content, and a fixed order
 * keeps the decision trace reproducible.
 */
public final class CredentialLeakGuardrail implements Guardrail {

  public static final String GUARDRAIL_NAME = "credential-leak";

  private final ScanToolArgumentsForSecretsUseCase useCase;
  private final InputAction onConfirmed;
  private final InputAction onSuspected;

  public CredentialLeakGuardrail(
      ScanToolArgumentsForSecretsUseCase useCase,
      InputAction onConfirmed,
      InputAction onSuspected) {
    this.useCase = Objects.requireNonNull(useCase, "useCase");
    this.onConfirmed = Objects.requireNonNull(onConfirmed, "onConfirmed");
    this.onSuspected = Objects.requireNonNull(onSuspected, "onSuspected");
  }

  @Override
  public String name() {
    return GUARDRAIL_NAME;
  }

  @Override
  public int order() {
    return 60;
  }

  @Override
  public GuardrailDecision evaluate(ToolInvocationContext context) {
    SecretScanResult result = useCase.scan(context.arguments());
    if (result.clean()) {
      return new Allow();
    }
    SecretSeverity severity = result.highestSeverity().orElseThrow();
    InputAction action = severity == SecretSeverity.CONFIRMED ? onConfirmed : onSuspected;
    String reason =
        "credential detected in tool arguments (%s)".formatted(describe(result.findings()));
    return switch (action) {
      case DENY -> new Deny(reason);
      case ESCALATE -> new Escalate(reason);
    };
  }

  static String describe(List<SecretFinding> findings) {
    return findings.stream()
        .map(SecretFinding::describe)
        .distinct()
        .collect(Collectors.joining(", "));
  }
}
