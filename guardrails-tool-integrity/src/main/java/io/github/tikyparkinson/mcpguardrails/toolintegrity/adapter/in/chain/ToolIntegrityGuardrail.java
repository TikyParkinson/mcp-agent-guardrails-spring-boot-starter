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
package io.github.tikyparkinson.mcpguardrails.toolintegrity.adapter.in.chain;

import io.github.tikyparkinson.mcpguardrails.core.application.port.out.Guardrail;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port.in.VerifyToolIntegrityUseCase;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port.out.ToolDefinitionCatalogPort;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.BaselineEstablished;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.Match;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.Mismatch;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.ToolDefinition;
import java.util.Objects;
import java.util.Optional;

/**
 * Anti tool-poisoning guardrail: verifies the invoked tool's current definition against its TOFU
 * baseline. A drifted definition without explicit approval is an attack signature — default action
 * is DENY. Runs early (order -50): trusting the tool itself precedes any decision about the agent.
 */
public final class ToolIntegrityGuardrail implements Guardrail {

  public static final String GUARDRAIL_NAME = "tool-integrity";

  private final VerifyToolIntegrityUseCase verify;
  private final ToolDefinitionCatalogPort catalog;
  private final MismatchAction onMismatch;
  private final UnknownDefinitionAction onUnknownDefinition;

  public ToolIntegrityGuardrail(
      VerifyToolIntegrityUseCase verify,
      ToolDefinitionCatalogPort catalog,
      MismatchAction onMismatch,
      UnknownDefinitionAction onUnknownDefinition) {
    this.verify = Objects.requireNonNull(verify, "verify");
    this.catalog = Objects.requireNonNull(catalog, "catalog");
    this.onMismatch = Objects.requireNonNull(onMismatch, "onMismatch");
    this.onUnknownDefinition = Objects.requireNonNull(onUnknownDefinition, "onUnknownDefinition");
  }

  @Override
  public String name() {
    return GUARDRAIL_NAME;
  }

  @Override
  public int order() {
    return -50;
  }

  @Override
  public GuardrailDecision evaluate(ToolInvocationContext context) {
    String toolName = context.toolName().value();
    Optional<ToolDefinition> definition = catalog.findByName(toolName);
    if (definition.isEmpty()) {
      return unknownDefinitionDecision(toolName);
    }
    return switch (verify.verify(definition.get())) {
      case BaselineEstablished _ -> new Allow();
      case Match _ -> new Allow();
      case Mismatch(var expected, var actual) ->
          mismatchDecision(toolName, expected.shortForm(), actual.shortForm());
    };
  }

  private GuardrailDecision unknownDefinitionDecision(String toolName) {
    String reason = "tool '%s' has no registered definition to verify against".formatted(toolName);
    return switch (onUnknownDefinition) {
      case ALLOW -> new Allow();
      case DENY -> new Deny(reason);
      case ESCALATE -> new Escalate(reason);
    };
  }

  private GuardrailDecision mismatchDecision(String toolName, String expected, String actual) {
    String reason =
        "tool '%s' definition drifted from approved baseline (expected %s, actual %s); "
                .formatted(toolName, expected, actual)
            + "approve the change to proceed";
    return switch (onMismatch) {
      case DENY -> new Deny(reason);
      case ESCALATE -> new Escalate(reason);
    };
  }
}
