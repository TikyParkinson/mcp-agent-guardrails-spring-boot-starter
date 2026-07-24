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
package io.github.tikyparkinson.mcpguardrails.injectionguard.adapter.out.rules;

import io.github.tikyparkinson.mcpguardrails.injectionguard.application.port.out.InjectionRuleSetPort;
import io.github.tikyparkinson.mcpguardrails.injectionguard.domain.InjectionRule;
import java.util.List;
import java.util.Objects;

/**
 * Default rule source: a fixed, immutable rule list (built by the starter from the built-in rules
 * plus configured custom rules). Replace it by exposing your own {@link InjectionRuleSetPort} bean
 * for dynamic rule feeds.
 */
public final class InMemoryInjectionRuleSetAdapter implements InjectionRuleSetPort {

  private final List<InjectionRule> rules;

  public InMemoryInjectionRuleSetAdapter(List<InjectionRule> rules) {
    this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
  }

  @Override
  public List<InjectionRule> activeRules() {
    return rules;
  }
}
