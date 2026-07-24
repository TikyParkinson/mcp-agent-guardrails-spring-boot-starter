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
package io.github.tikyparkinson.mcpguardrails.injectionguard.domain;

import java.util.List;

/**
 * Built-in detection rules with stable ids. Business knowledge of this guardrail, not framework
 * configuration: properties only decide whether they are used and may add custom rules.
 */
public final class BuiltInInjectionRules {

  private BuiltInInjectionRules() {}

  /** The default rule set, in evaluation order. */
  public static List<InjectionRule> defaults() {
    return List.of(
        InjectionRule.of(
            "ignore-previous-instructions",
            "ignore\\s+(all\\s+)?(previous|prior|above)\\s+(instructions|prompts?|rules)",
            InjectionSeverity.MALICIOUS),
        InjectionRule.of(
            "reveal-system-prompt",
            "(reveal|show|print|repeat)\\s+(your|the)\\s+(system\\s+)?prompt",
            InjectionSeverity.MALICIOUS),
        InjectionRule.of(
            "override-role", "you\\s+are\\s+(now|no\\s+longer)\\s+", InjectionSeverity.MALICIOUS),
        InjectionRule.of(
            "disregard-safety",
            "(disregard|bypass|disable)\\s+(your\\s+)?(safety|guardrails?|filters?|restrictions)",
            InjectionSeverity.MALICIOUS),
        InjectionRule.of(
            "do-anything-now",
            "\\b(DAN\\s+mode|do\\s+anything\\s+now|jailbreak)\\b",
            InjectionSeverity.SUSPICIOUS),
        InjectionRule.of("base64-blob", "[A-Za-z0-9+/]{200,}={0,2}", InjectionSeverity.SUSPICIOUS));
  }
}
