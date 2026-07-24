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
package io.github.tikyparkinson.mcpguardrails.authz.domain;

import java.util.Objects;

/**
 * One authorization rule. Patterns are either an exact, case-sensitive match or the full wildcard
 * {@code "*"} — deliberately nothing in between (partial globs invite security mistakes that are
 * hard to audit).
 */
public record PolicyRule(String agentPattern, String toolPattern, PermissionEffect effect) {

  private static final String WILDCARD = "*";

  public PolicyRule {
    Objects.requireNonNull(effect, "effect");
    requireNotBlank(agentPattern, "agentPattern");
    requireNotBlank(toolPattern, "toolPattern");
  }

  /** True when both patterns accept the given agent and tool. */
  public boolean matches(String agentId, String toolName) {
    return matchesPattern(agentPattern, agentId) && matchesPattern(toolPattern, toolName);
  }

  private static boolean matchesPattern(String pattern, String value) {
    return WILDCARD.equals(pattern) || pattern.equals(value);
  }

  private static void requireNotBlank(String value, String field) {
    Objects.requireNonNull(value, field);
    if (value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
