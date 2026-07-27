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
package io.github.tikyparkinson.mcpguardrails.approval.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalPolicy;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class GuardrailsApprovalPropertiesTest {

  private static final String PREFIX = "mcp.guardrails.approval";

  @Test
  void shouldUseTheDocumentedDefaultsWhenNothingIsConfigured() {
    // given no configuration at all
    // when bound
    GuardrailsApprovalProperties properties = bind(Map.of());

    // then the defaults match what the README promises
    assertEquals(
        new GuardrailsApprovalProperties(true, Duration.ofMinutes(2), 20, 5, true), properties);
  }

  @Test
  void shouldApplyEveryConfiguredProperty() {
    // given every property overridden
    Map<String, Object> yaml = new LinkedHashMap<>();
    yaml.put(PREFIX + ".enabled", "false");
    yaml.put(PREFIX + ".timeout", "PT30S");
    yaml.put(PREFIX + ".max-pending", "7");
    yaml.put(PREFIX + ".max-pending-per-agent", "3");
    yaml.put(PREFIX + ".include-arguments", "false");

    // when bound
    GuardrailsApprovalProperties properties = bind(yaml);

    // then all of them arrive: a record missing @ConstructorBinding would silently keep defaults
    assertEquals(
        new GuardrailsApprovalProperties(false, Duration.ofSeconds(30), 7, 3, false), properties);
  }

  @Test
  void shouldBuildThePolicyFromTheConfiguration() {
    // given a configuration
    GuardrailsApprovalProperties properties =
        new GuardrailsApprovalProperties(true, Duration.ofMinutes(5), 30, 4, false);

    // when the policy is built
    // then it carries the gate settings
    assertEquals(new ApprovalPolicy(Duration.ofMinutes(5), 30, 4, false), properties.toPolicy());
  }

  @Test
  void shouldFailWhenTheAgentQuotaExceedsTheGlobalOne() {
    // given a per-agent quota larger than the channel itself
    GuardrailsApprovalProperties properties =
        new GuardrailsApprovalProperties(true, Duration.ofMinutes(2), 3, 10, true);

    // when the policy is built
    // then start-up fails: the per-agent limit would be unreachable and therefore meaningless
    IllegalArgumentException failure =
        assertThrows(IllegalArgumentException.class, properties::toPolicy);
    assertTrue(failure.getMessage().contains("must not exceed maxPending"), failure.getMessage());
  }

  @Test
  void shouldFailWhenTheTimeoutIsNotPositive() {
    // given a timeout of zero
    GuardrailsApprovalProperties properties =
        new GuardrailsApprovalProperties(true, Duration.ZERO, 20, 5, true);

    // when the policy is built
    // then it fails: nothing could ever be approved in time
    assertThrows(IllegalArgumentException.class, properties::toPolicy);
  }

  @Test
  void shouldKeepTheGateEnabledUnlessTurnedOff() {
    // given no configuration
    // when bound
    // then the gate is on: a module you added to the classpath and that quietly does nothing is
    // worse than one that is not there
    assertTrue(bind(Map.of()).enabled());
  }

  private static GuardrailsApprovalProperties bind(Map<String, Object> yaml) {
    return new Binder(new MapConfigurationPropertySource(yaml))
        .bind(PREFIX, GuardrailsApprovalProperties.class)
        .orElseGet(GuardrailsApprovalProperties::new);
  }
}
