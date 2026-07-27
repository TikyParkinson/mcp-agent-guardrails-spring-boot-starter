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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.core.domain.ScanBudget;
import io.github.tikyparkinson.mcpguardrails.injectionguard.domain.InjectionRule;
import io.github.tikyparkinson.mcpguardrails.injectionguard.domain.InjectionSeverity;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuardrailsInjectionGuardPropertiesTest {

  @Test
  void shouldEnableWithBuiltInsAndNoCustomsWhenDefaultConstructorUsed() {
    // given / when
    GuardrailsInjectionGuardProperties properties = new GuardrailsInjectionGuardProperties();

    // then
    assertTrue(properties.enabled());
    assertTrue(properties.builtInRulesEnabled());
    assertEquals(List.of(), properties.customRules());
    assertEquals(6, properties.toRules().size());
  }

  @Test
  void shouldAppendCustomRulesAfterBuiltInsWhenConfigured() {
    // given
    GuardrailsInjectionGuardProperties properties =
        new GuardrailsInjectionGuardProperties(
            true,
            true,
            List.of(
                new GuardrailsInjectionGuardProperties.CustomRule(
                    "internal-hosts", "corp\\.internal", InjectionSeverity.SUSPICIOUS)));

    // when
    List<InjectionRule> rules = properties.toRules();

    // then
    assertEquals(7, rules.size());
    assertEquals("internal-hosts", rules.get(6).id());
  }

  @Test
  void shouldExcludeBuiltInsWhenDisabled() {
    // given
    GuardrailsInjectionGuardProperties properties =
        new GuardrailsInjectionGuardProperties(
            true,
            false,
            List.of(
                new GuardrailsInjectionGuardProperties.CustomRule(
                    "only-mine", "x", InjectionSeverity.MALICIOUS)));

    // when
    List<InjectionRule> rules = properties.toRules();

    // then
    assertEquals(1, rules.size());
    assertEquals("only-mine", rules.get(0).id());
  }

  @Test
  void shouldNormalizeNullCustomRulesWhenBoundWithMissingValues() {
    // given / when
    GuardrailsInjectionGuardProperties properties =
        new GuardrailsInjectionGuardProperties(true, true, null);

    // then
    assertEquals(List.of(), properties.customRules());
  }

  @Test
  void shouldDescribeTheDefaultBudgetWhenNoLimitsAreConfigured() {
    // given / when
    ScanBudget budget = new GuardrailsInjectionGuardProperties().toBudget();

    // then
    assertEquals(ScanBudget.defaults(), budget);
  }

  @Test
  void shouldDescribeTheConfiguredBudgetWhenLimitsAreOverridden() {
    // given
    GuardrailsInjectionGuardProperties properties =
        new GuardrailsInjectionGuardProperties(true, true, List.of(), 25, 3);

    // when
    ScanBudget budget = properties.toBudget();

    // then
    assertEquals(new ScanBudget(25, 3), budget);
  }
}
