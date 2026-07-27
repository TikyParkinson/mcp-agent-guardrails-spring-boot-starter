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
package io.github.tikyparkinson.mcpguardrails.anomaly.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.anomaly.domain.AnomalyPolicy;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class GuardrailsAnomalyPropertiesTest {

  private static final String PREFIX = "mcp.guardrails.anomaly";

  @Test
  void shouldUseTheDocumentedDefaultsWhenNothingIsConfigured() {
    // given no configuration at all
    // when bound
    GuardrailsAnomalyProperties properties = bind(Map.of());

    // then the defaults match what the README promises
    assertEquals(
        new GuardrailsAnomalyProperties(
            true, Duration.ofMinutes(1), 5, 3, 20L, Duration.ofMinutes(30), 500),
        properties);
  }

  @Test
  void shouldApplyEveryConfiguredProperty() {
    // given every property overridden
    Map<String, Object> yaml = new LinkedHashMap<>();
    yaml.put(PREFIX + ".enabled", "false");
    yaml.put(PREFIX + ".window", "PT2M");
    yaml.put(PREFIX + ".repeat-threshold", "9");
    yaml.put(PREFIX + ".novel-tool-threshold", "7");
    yaml.put(PREFIX + ".baseline-min-invocations", "44");
    yaml.put(PREFIX + ".retention", "PT10M");
    yaml.put(PREFIX + ".max-records-per-agent", "123");

    // when bound
    GuardrailsAnomalyProperties properties = bind(yaml);

    // then all of them arrive: a record missing @ConstructorBinding would silently keep defaults
    assertEquals(
        new GuardrailsAnomalyProperties(
            false, Duration.ofMinutes(2), 9, 7, 44L, Duration.ofMinutes(10), 123),
        properties);
  }

  @Test
  void shouldFailWhenRetentionIsShorterThanTheWindow() {
    // given a retention shorter than the analysis window
    Map<String, Object> yaml = new LinkedHashMap<>();
    yaml.put(PREFIX + ".window", "PT10M");
    yaml.put(PREFIX + ".retention", "PT1M");

    // when bound
    BindException failure = assertThrows(BindException.class, () -> bind(yaml));

    // then start-up fails: the analysis would otherwise read a history already thrown away, and
    // the detector would quietly find nothing
    assertTrue(rootCauseOf(failure).getMessage().contains("must be at least as long as window"));
  }

  @Test
  void shouldAcceptARetentionEqualToTheWindow() {
    // given a retention exactly as long as the window
    Map<String, Object> yaml = new LinkedHashMap<>();
    yaml.put(PREFIX + ".window", "PT5M");
    yaml.put(PREFIX + ".retention", "PT5M");

    // when bound
    // then it is accepted: the bound is inclusive
    assertEquals(Duration.ofMinutes(5), bind(yaml).retention());
  }

  @Test
  void shouldBuildThePolicyFromTheConfiguration() {
    // given a configuration
    GuardrailsAnomalyProperties properties =
        new GuardrailsAnomalyProperties(
            true, Duration.ofMinutes(3), 6, 4, 30L, Duration.ofHours(1), 100);

    // when the policy is built
    // then it carries the analysis settings, and only those
    assertEquals(new AnomalyPolicy(Duration.ofMinutes(3), 6, 4, 30L), properties.toPolicy());
  }

  @Test
  void shouldFailWhenTheRepeatThresholdCannotDetectARepetition() {
    // given a repeat threshold of one
    GuardrailsAnomalyProperties properties =
        new GuardrailsAnomalyProperties(
            true, Duration.ofMinutes(1), 1, 3, 20L, Duration.ofMinutes(30), 500);

    // when the policy is built
    // then it fails: such a policy would report every single call as a loop
    assertThrows(IllegalArgumentException.class, properties::toPolicy);
  }

  @Test
  void shouldNotFailTheComparisonWhenADurationIsAbsent() {
    // given a configuration built without durations, as binding can produce mid-resolution
    GuardrailsAnomalyProperties properties =
        new GuardrailsAnomalyProperties(true, null, 5, 3, 20L, null, 500);

    // when the retention check runs
    // then it does not throw a NullPointerException of its own: the binder must be free to report
    // the missing value itself, with the property name, instead of a stack trace from in here
    assertNull(properties.window());
    assertThrows(NullPointerException.class, properties::toPolicy);
  }

  @Test
  void shouldNotFailTheComparisonWhenTheWindowAloneIsAbsent() {
    // given a retention but no window
    GuardrailsAnomalyProperties properties =
        new GuardrailsAnomalyProperties(true, null, 5, 3, 20L, Duration.ofMinutes(30), 500);

    // when the retention check runs
    // then it is skipped rather than comparing against nothing
    assertNull(properties.window());
  }

  private static GuardrailsAnomalyProperties bind(Map<String, Object> yaml) {
    return new Binder(new MapConfigurationPropertySource(yaml))
        .bind(PREFIX, GuardrailsAnomalyProperties.class)
        .orElseGet(GuardrailsAnomalyProperties::new);
  }

  private static Throwable rootCauseOf(Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }
}
