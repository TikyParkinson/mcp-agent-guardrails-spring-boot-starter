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
package io.github.tikyparkinson.mcpguardrails.injectionguard.infrastructure;

import io.github.tikyparkinson.mcpguardrails.core.domain.ScanBudget;
import io.github.tikyparkinson.mcpguardrails.injectionguard.domain.BuiltInInjectionRules;
import io.github.tikyparkinson.mcpguardrails.injectionguard.domain.InjectionRule;
import io.github.tikyparkinson.mcpguardrails.injectionguard.domain.InjectionSeverity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Injection-guard configuration, bound to the {@code mcp.guardrails.injection-guard} prefix.
 *
 * @param enabled whether the injection guardrail is registered. Default: {@code true}.
 * @param builtInRulesEnabled whether the built-in rule set is included. Default: {@code true}.
 * @param customRules additional rules appended after the built-in ones. Default: empty.
 * @param maxScanNodes values examined before the scan gives up. Default: {@code 10000}. Reaching it
 *     denies the call rather than allowing it: arguments nobody finished looking at are not
 *     arguments known to be clean. The worst case it admits costs about 3 ms.
 * @param maxScanDepth nesting levels explored before the scan gives up. Default: {@code 64}. A
 *     guard against runaway recursion, not a cost control — depth is cheap, node count is not.
 */
@ConfigurationProperties(prefix = "mcp.guardrails.injection-guard")
public record GuardrailsInjectionGuardProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("true") boolean builtInRulesEnabled,
    List<CustomRule> customRules,
    @DefaultValue("10000") int maxScanNodes,
    @DefaultValue("64") int maxScanDepth) {

  @ConstructorBinding
  public GuardrailsInjectionGuardProperties {
    customRules = customRules == null ? List.of() : List.copyOf(customRules);
  }

  /** Default configuration: enabled, built-in rules on, no custom rules, default budget. */
  public GuardrailsInjectionGuardProperties() {
    this(true, true, List.of());
  }

  /**
   * The form this record had before the scan budget existed, kept so callers that predate it still
   * compile. Uses the default budget.
   */
  public GuardrailsInjectionGuardProperties(
      boolean enabled, boolean builtInRulesEnabled, List<CustomRule> customRules) {
    this(
        enabled,
        builtInRulesEnabled,
        customRules,
        ScanBudget.defaults().maxNodes(),
        ScanBudget.defaults().maxDepth());
  }

  /** The scan budget this configuration describes. */
  public ScanBudget toBudget() {
    return new ScanBudget(maxScanNodes, maxScanDepth);
  }

  /** Builds the rule list this configuration describes (built-ins first, then customs). */
  public List<InjectionRule> toRules() {
    List<InjectionRule> rules = new ArrayList<>();
    if (builtInRulesEnabled) {
      rules.addAll(BuiltInInjectionRules.defaults());
    }
    for (CustomRule custom : customRules) {
      rules.add(InjectionRule.of(custom.id(), custom.pattern(), custom.severity()));
    }
    return List.copyOf(rules);
  }

  /**
   * One configured custom rule.
   *
   * @param id stable rule id, used in audit trails.
   * @param pattern regex, compiled case-insensitive.
   * @param severity MALICIOUS denies, SUSPICIOUS escalates.
   */
  public record CustomRule(String id, String pattern, InjectionSeverity severity) {}
}
