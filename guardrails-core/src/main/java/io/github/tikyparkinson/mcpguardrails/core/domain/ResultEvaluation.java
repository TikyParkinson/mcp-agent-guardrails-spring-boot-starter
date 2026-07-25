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

import java.util.Objects;

/** Decision emitted by one named outbound guardrail during a result evaluation. */
public record ResultEvaluation(String guardrailName, ResultDecision decision) {

  public ResultEvaluation {
    Objects.requireNonNull(guardrailName, "guardrailName");
    Objects.requireNonNull(decision, "decision");
    if (guardrailName.isBlank()) {
      throw new IllegalArgumentException("guardrailName must not be blank");
    }
  }
}
