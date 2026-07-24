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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.authz.domain.AccessPolicy;
import io.github.tikyparkinson.mcpguardrails.authz.domain.PermissionEffect;
import io.github.tikyparkinson.mcpguardrails.authz.domain.PolicyDecision;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuardrailsAuthzPropertiesTest {

  @Test
  void shouldEnableWithAllowDefaultAndNoRulesWhenDefaultConstructorUsed() {
    // given / when
    GuardrailsAuthzProperties properties = new GuardrailsAuthzProperties();

    // then
    assertTrue(properties.enabled());
    assertEquals(PermissionEffect.ALLOW, properties.defaultEffect());
    assertEquals(List.of(), properties.rules());
  }

  @Test
  void shouldNormalizeNullsWhenBoundWithMissingValues() {
    // given: Spring binding may pass nulls for absent properties
    GuardrailsAuthzProperties properties = new GuardrailsAuthzProperties(true, null, null);

    // then
    assertEquals(PermissionEffect.ALLOW, properties.defaultEffect());
    assertEquals(List.of(), properties.rules());
  }

  @Test
  void shouldBuildDomainPolicyWhenRulesConfigured() {
    // given
    GuardrailsAuthzProperties properties =
        new GuardrailsAuthzProperties(
            true,
            PermissionEffect.DENY,
            List.of(
                new GuardrailsAuthzProperties.Rule("agent-1", "*", PermissionEffect.ALLOW),
                new GuardrailsAuthzProperties.Rule("*", "drop_table", PermissionEffect.ESCALATE)));

    // when
    AccessPolicy policy = properties.toAccessPolicy();

    // then
    assertEquals(
        new PolicyDecision(PermissionEffect.ALLOW, "rule[0]"), policy.decide("agent-1", "x"));
    assertEquals(
        new PolicyDecision(PermissionEffect.ESCALATE, "rule[1]"),
        policy.decide("agent-2", "drop_table"));
    assertEquals(
        new PolicyDecision(PermissionEffect.DENY, "default"), policy.decide("agent-2", "y"));
  }
}
