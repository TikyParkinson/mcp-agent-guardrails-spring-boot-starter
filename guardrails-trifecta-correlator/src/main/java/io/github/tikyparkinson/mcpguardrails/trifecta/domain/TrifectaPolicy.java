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
package io.github.tikyparkinson.mcpguardrails.trifecta.domain;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Correlator settings, validated once at construction rather than on every invocation.
 *
 * @param tools tools the operator declared, with the legs each one touches; an empty list means
 *     this guardrail detects nothing, which the module announces at start-up rather than pretending
 *     to protect
 * @param sessionIdleTimeout how long without invocations before a session is forgotten
 * @param sessionMaxDuration how long a session may live from its first invocation, whatever its
 *     idleness. Both are needed: a busy agent refreshes the idle clock on every call and would
 *     never reach the first bound, so a trifecta closed in the morning would still be escalating
 *     the next day
 */
public record TrifectaPolicy(
    List<ToolCapabilities> tools, Duration sessionIdleTimeout, Duration sessionMaxDuration) {

  public TrifectaPolicy {
    tools = List.copyOf(Objects.requireNonNull(tools, "tools"));
    requirePositive(sessionIdleTimeout, "sessionIdleTimeout");
    requirePositive(sessionMaxDuration, "sessionMaxDuration");
    if (sessionMaxDuration.compareTo(sessionIdleTimeout) < 0) {
      throw new IllegalArgumentException(
          "sessionMaxDuration (%s) must not be shorter than sessionIdleTimeout (%s)"
              .formatted(sessionMaxDuration, sessionIdleTimeout));
    }
    requireDistinctToolNames(tools);
  }

  /**
   * The legs the given tool touches, or an empty set when it was not declared. An undeclared tool
   * contributes nothing rather than being guessed at.
   */
  public Set<Capability> capabilitiesOf(String toolName) {
    Objects.requireNonNull(toolName, "toolName");
    return tools.stream()
        .filter(tool -> tool.toolName().equals(toolName))
        .findFirst()
        .map(ToolCapabilities::capabilities)
        .orElseGet(Set::of);
  }

  /** True when no tool was declared, so nothing can ever be correlated. */
  public boolean declaresNothing() {
    return tools.isEmpty();
  }

  private static void requirePositive(Duration value, String field) {
    Objects.requireNonNull(value, field);
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(field + " must be positive, was " + value);
    }
  }

  private static void requireDistinctToolNames(List<ToolCapabilities> tools) {
    Set<String> seen = new HashSet<>();
    for (ToolCapabilities tool : tools) {
      if (!seen.add(tool.toolName())) {
        throw new IllegalArgumentException("tool '" + tool.toolName() + "' is declared twice");
      }
    }
  }
}
