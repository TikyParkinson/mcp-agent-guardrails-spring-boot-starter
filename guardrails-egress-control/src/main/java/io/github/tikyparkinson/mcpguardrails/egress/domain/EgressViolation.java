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
package io.github.tikyparkinson.mcpguardrails.egress.domain;

import java.util.List;
import java.util.Objects;

/**
 * The invocation must not proceed.
 *
 * <p>The two lists are kept apart because they tell the operator different things: {@code
 * violations} means the allowlist is missing an entry, {@code undeterminedArguments} means the
 * destination could not be read at all — which on a declared egress tool is exactly what an
 * obfuscated target looks like.
 */
public record EgressViolation(List<Destination> violations, List<String> undeterminedArguments)
    implements EgressCheckResult {

  public EgressViolation {
    violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
    undeterminedArguments =
        List.copyOf(Objects.requireNonNull(undeterminedArguments, "undeterminedArguments"));
    if (violations.isEmpty() && undeterminedArguments.isEmpty()) {
      throw new IllegalArgumentException("a violation must carry at least one reason");
    }
  }
}
