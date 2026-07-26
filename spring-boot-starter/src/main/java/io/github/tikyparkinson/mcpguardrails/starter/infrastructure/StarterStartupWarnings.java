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
package io.github.tikyparkinson.mcpguardrails.starter.infrastructure;

import io.github.tikyparkinson.mcpguardrails.core.application.port.out.EscalationResolver;
import java.util.List;
import java.util.Objects;

/**
 * What the assembled set of guardrails cannot do with the beans it was given.
 *
 * <p>A wiring that is present but inert is indistinguishable from one that works, and an operator
 * who believes in a protection they do not have is worse off than one who knows they have none.
 * ARCHITECTURE.md §5.2 makes announcing that a requirement rather than a courtesy.
 */
public final class StarterStartupWarnings {

  private StarterStartupWarnings() {}

  /**
   * Warnings for the given wiring, empty when nothing is worth saying.
   *
   * @param escalationResolver the resolver in the context, or {@code null} when there is none
   * @param escalatingGuardrails names of guardrails that can return {@code Escalate}
   */
  public static List<String> of(
      EscalationResolver escalationResolver, List<String> escalatingGuardrails) {
    Objects.requireNonNull(escalatingGuardrails, "escalatingGuardrails");
    if (escalationResolver != null || escalatingGuardrails.isEmpty()) {
      return List.of();
    }
    return List.of(
        "%s can escalate an invocation, but no EscalationResolver is registered, so an escalation"
                .formatted(String.join(", ", escalatingGuardrails))
            + " returns an error to the agent instead of reaching a person. That is fail-closed but"
            + " indistinguishable from a failure. Add guardrails-approval-gate to the classpath, or"
            + " publish your own EscalationResolver bean.");
  }
}
