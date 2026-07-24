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

import java.util.List;
import java.util.Objects;

/**
 * Ordered authorization policy: first matching rule wins; when no rule matches, the default effect
 * applies.
 */
public record AccessPolicy(List<PolicyRule> rules, PermissionEffect defaultEffect) {

  public AccessPolicy {
    Objects.requireNonNull(defaultEffect, "defaultEffect");
    rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
  }

  /** Evaluates this policy for the given agent and tool. Never null. */
  public PolicyDecision decide(String agentId, String toolName) {
    Objects.requireNonNull(agentId, "agentId");
    Objects.requireNonNull(toolName, "toolName");
    for (int i = 0; i < rules.size(); i++) {
      if (rules.get(i).matches(agentId, toolName)) {
        return new PolicyDecision(rules.get(i).effect(), "rule[" + i + "]");
      }
    }
    return new PolicyDecision(defaultEffect, "default");
  }
}
