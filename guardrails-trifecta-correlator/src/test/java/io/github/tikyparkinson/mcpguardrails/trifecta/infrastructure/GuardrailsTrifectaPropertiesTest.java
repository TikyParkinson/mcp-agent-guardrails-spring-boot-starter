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
package io.github.tikyparkinson.mcpguardrails.trifecta.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.trifecta.domain.Capability;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.ToolCapabilities;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.TrifectaPolicy;
import io.github.tikyparkinson.mcpguardrails.trifecta.infrastructure.GuardrailsTrifectaProperties.ToolConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class GuardrailsTrifectaPropertiesTest {

  private static final String PREFIX = "mcp.guardrails.trifecta";

  @Test
  void shouldUseTheDocumentedDefaultsWhenNothingIsConfigured() {
    // given no configuration at all
    // when bound
    GuardrailsTrifectaProperties properties = bind(Map.of());

    // then the defaults match what the README promises
    assertEquals(
        new GuardrailsTrifectaProperties(
            true, Duration.ofMinutes(30), Duration.ofHours(2), List.of()),
        properties);
  }

  @Test
  void shouldDetectNothingWhenNoToolIsDeclared() {
    // given the default configuration
    // when the policy is built
    // then it admits it can detect nothing. Failing closed on ignorance would make the server
    // unusable on first boot, but staying silent would let an operator believe in a protection
    // that is not running
    assertTrue(bind(Map.of()).toPolicy().declaresNothing());
  }

  @Test
  void shouldApplyEveryConfiguredProperty() {
    // given every property overridden
    Map<String, Object> yaml = new LinkedHashMap<>();
    yaml.put(PREFIX + ".enabled", "false");
    yaml.put(PREFIX + ".session-idle-timeout", "PT10M");
    yaml.put(PREFIX + ".session-max-duration", "PT1H");
    yaml.put(PREFIX + ".tools[0].name", "read_customer_record");
    yaml.put(PREFIX + ".tools[0].capabilities[0]", "PRIVATE_DATA");

    // when bound
    GuardrailsTrifectaProperties properties = bind(yaml);

    // then all of them arrive: a record missing @ConstructorBinding would silently keep defaults
    assertEquals(
        new GuardrailsTrifectaProperties(
            false,
            Duration.ofMinutes(10),
            Duration.ofHours(1),
            List.of(new ToolConfig("read_customer_record", Set.of(Capability.PRIVATE_DATA)))),
        properties);
  }

  @Test
  void shouldBindAToolTouchingSeveralLegs() {
    // given a tool declared with two legs
    Map<String, Object> yaml = new LinkedHashMap<>();
    yaml.put(PREFIX + ".tools[0].name", "fetch_url");
    yaml.put(PREFIX + ".tools[0].capabilities[0]", "UNTRUSTED_CONTENT");
    yaml.put(PREFIX + ".tools[0].capabilities[1]", "EXTERNAL_COMMS");

    // when the policy is built
    // then both reach it: one tool can close two thirds of the triangle by itself
    assertEquals(
        Set.of(Capability.UNTRUSTED_CONTENT, Capability.EXTERNAL_COMMS),
        bind(yaml).toPolicy().capabilitiesOf("fetch_url"));
  }

  @Test
  void shouldRejectACapabilityThatDoesNotExist() {
    // given a misspelt capability
    Map<String, Object> yaml = new LinkedHashMap<>();
    yaml.put(PREFIX + ".tools[0].name", "x");
    yaml.put(PREFIX + ".tools[0].capabilities[0]", "PRIVATE_DATAA");

    // when bound
    // then start-up fails rather than quietly declaring a tool with no legs
    assertThrows(RuntimeException.class, () -> bind(yaml));
  }

  @Test
  void shouldFailWithTheToolNameWhenItDeclaresNoCapability() {
    // given a tool declared with no legs at all
    Map<String, Object> yaml = new LinkedHashMap<>();
    yaml.put(PREFIX + ".tools[0].name", "read_customer");

    // when the policy is built
    // then the message names the tool, so the operator knows which line of YAML to fix
    GuardrailsTrifectaProperties properties = bind(yaml);
    IllegalArgumentException failure =
        assertThrows(IllegalArgumentException.class, properties::toPolicy);
    assertTrue(failure.getMessage().contains("read_customer"), failure.getMessage());
  }

  @Test
  void shouldFailWhenTheMaximumDurationIsShorterThanTheIdleTimeout() {
    // given an absolute bound shorter than the idle one
    GuardrailsTrifectaProperties properties =
        new GuardrailsTrifectaProperties(
            true, Duration.ofHours(2), Duration.ofMinutes(10), List.of());

    // when the policy is built
    // then start-up fails: the idle bound would be unreachable, so configuring it would be a lie
    IllegalArgumentException failure =
        assertThrows(IllegalArgumentException.class, properties::toPolicy);
    assertTrue(failure.getMessage().contains("must not be shorter"), failure.getMessage());
  }

  @Test
  void shouldBuildThePolicyFromTheConfiguration() {
    // given a configuration
    GuardrailsTrifectaProperties properties =
        new GuardrailsTrifectaProperties(
            true,
            Duration.ofMinutes(5),
            Duration.ofMinutes(50),
            List.of(new ToolConfig("send_email", Set.of(Capability.EXTERNAL_COMMS))));

    // when the policy is built
    // then it carries the declarations and both bounds
    assertEquals(
        new TrifectaPolicy(
            List.of(new ToolCapabilities("send_email", Set.of(Capability.EXTERNAL_COMMS))),
            Duration.ofMinutes(5),
            Duration.ofMinutes(50)),
        properties.toPolicy());
  }

  @Test
  void shouldTreatAMissingToolListAsNothingDeclared() {
    // given a configuration built with no list at all
    GuardrailsTrifectaProperties properties =
        new GuardrailsTrifectaProperties(true, Duration.ofMinutes(30), Duration.ofHours(2), null);

    // when read
    // then it is an empty list rather than null, so nothing downstream has to check
    assertEquals(List.of(), properties.tools());
  }

  @Test
  void shouldCopyTheToolListDefensively() {
    // given a mutable list handed to the configuration
    List<ToolConfig> tools =
        new ArrayList<>(List.of(new ToolConfig("x", Set.of(Capability.PRIVATE_DATA))));
    GuardrailsTrifectaProperties properties =
        new GuardrailsTrifectaProperties(true, Duration.ofMinutes(30), Duration.ofHours(2), tools);

    // when the caller clears its own list afterwards
    tools.clear();

    // then the configuration still knows the tool
    assertEquals(1, properties.tools().size());
  }

  @Test
  void shouldTreatAToolWithoutCapabilitiesAsAnEmptySet() {
    // given a tool config built with no capability set
    ToolConfig config = new ToolConfig("x", null);

    // when read
    // then it holds an empty set, which lets the domain reject it with a message naming the tool
    // rather than failing with a bare null
    assertEquals(Set.of(), config.capabilities());
  }

  @Test
  void shouldKeepTheGuardrailEnabledUnlessTurnedOff() {
    // given no configuration
    // when bound
    // then the guardrail is on
    assertTrue(bind(Map.of()).enabled());
  }

  @Test
  void shouldWarnAtStartUpWhenNothingIsDeclared() {
    // given a policy with no tools
    TrifectaPolicy policy = bind(Map.of()).toPolicy();

    // when the start-up warnings are collected
    List<String> warnings = TrifectaStartupWarnings.of(policy);

    // then there is one, saying the guardrail will never detect anything: registered but inert is
    // indistinguishable from working, and ARCHITECTURE.md §5.2 makes announcing that a requirement
    assertEquals(1, warnings.size());
    assertTrue(warnings.getFirst().contains("never detect anything"), warnings.getFirst());
  }

  @Test
  void shouldNotWarnWhenToolsAreDeclared() {
    // given a policy with a declared tool
    Map<String, Object> yaml = new LinkedHashMap<>();
    yaml.put(PREFIX + ".tools[0].name", "read_customer");
    yaml.put(PREFIX + ".tools[0].capabilities[0]", "PRIVATE_DATA");

    // when the start-up warnings are collected
    // then there is nothing to say
    assertEquals(List.of(), TrifectaStartupWarnings.of(bind(yaml).toPolicy()));
  }

  @Test
  void shouldExplainWhyCorrelatingByAgentIsWorse() {
    // given the fallback warning
    String warning = TrifectaStartupWarnings.agentFallbackWarning();

    // when read
    // then it says what actually goes wrong, not just that a fallback happened: an operator who
    // only reads "falling back" has no reason to act
    assertTrue(warning.contains("shared by every"), warning);
    assertTrue(warning.contains("SessionIdResolver"), warning);
  }

  @Test
  void shouldRejectCollectingWarningsWithoutAPolicy() {
    // given no policy
    // when the warnings are collected
    // then it fails
    assertThrows(NullPointerException.class, () -> TrifectaStartupWarnings.of(null));
  }

  @Test
  void shouldNotWarnAboutAnythingElseYet() {
    // given a fully configured policy
    Map<String, Object> yaml = new LinkedHashMap<>();
    yaml.put(PREFIX + ".tools[0].name", "read_customer");
    yaml.put(PREFIX + ".tools[0].capabilities[0]", "PRIVATE_DATA");

    // when the warnings are collected
    // then the list is empty: warnings are for things an operator can act on, not noise
    assertFalse(TrifectaStartupWarnings.of(bind(yaml).toPolicy()).iterator().hasNext());
  }

  private static GuardrailsTrifectaProperties bind(Map<String, Object> yaml) {
    return new Binder(new MapConfigurationPropertySource(yaml))
        .bind(PREFIX, GuardrailsTrifectaProperties.class)
        .orElseGet(GuardrailsTrifectaProperties::new);
  }
}
