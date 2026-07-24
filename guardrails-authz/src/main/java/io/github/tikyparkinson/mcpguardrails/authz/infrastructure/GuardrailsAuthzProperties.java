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
package io.github.tikyparkinson.mcpguardrails.authz.infrastructure;

import io.github.tikyparkinson.mcpguardrails.authz.domain.AccessPolicy;
import io.github.tikyparkinson.mcpguardrails.authz.domain.PermissionEffect;
import io.github.tikyparkinson.mcpguardrails.authz.domain.PolicyRule;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Authz guardrail configuration, bound to the {@code mcp.guardrails.authz} prefix.
 *
 * @param enabled whether the authz guardrail is registered. Default: {@code true}.
 * @param defaultEffect effect when no rule matches. Default: {@code ALLOW} (the starter must work
 *     with zero configuration; opt into default-deny with one property line).
 * @param rules ordered rule list; first match wins. Default: empty.
 */
@ConfigurationProperties(prefix = "mcp.guardrails.authz")
public record GuardrailsAuthzProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("ALLOW") PermissionEffect defaultEffect,
    List<Rule> rules) {

  @ConstructorBinding
  public GuardrailsAuthzProperties {
    defaultEffect = defaultEffect == null ? PermissionEffect.ALLOW : defaultEffect;
    rules = rules == null ? List.of() : List.copyOf(rules);
  }

  /** Default configuration: enabled, allow-by-default, no rules. */
  public GuardrailsAuthzProperties() {
    this(true, PermissionEffect.ALLOW, List.of());
  }

  /** Builds the immutable domain policy this configuration describes. */
  public AccessPolicy toAccessPolicy() {
    List<PolicyRule> policyRules =
        rules.stream()
            .map(rule -> new PolicyRule(rule.agent(), rule.tool(), rule.effect()))
            .toList();
    return new AccessPolicy(policyRules, defaultEffect);
  }

  /**
   * One configured rule.
   *
   * @param agent agent pattern: exact match or {@code "*"}.
   * @param tool tool pattern: exact match or {@code "*"}.
   * @param effect effect applied when the rule matches.
   */
  public record Rule(String agent, String tool, PermissionEffect effect) {}
}
