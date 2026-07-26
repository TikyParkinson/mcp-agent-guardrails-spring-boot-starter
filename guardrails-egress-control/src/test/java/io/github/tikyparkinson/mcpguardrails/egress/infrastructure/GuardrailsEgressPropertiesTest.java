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
package io.github.tikyparkinson.mcpguardrails.egress.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.egress.adapter.in.chain.ViolationAction;
import io.github.tikyparkinson.mcpguardrails.egress.domain.Destination;
import io.github.tikyparkinson.mcpguardrails.egress.domain.EgressPolicy;
import io.github.tikyparkinson.mcpguardrails.egress.domain.EgressTool;
import io.github.tikyparkinson.mcpguardrails.egress.infrastructure.GuardrailsEgressProperties.ToolConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuardrailsEgressPropertiesTest {

  private static GuardrailsEgressProperties properties(
      List<String> allowed, List<ToolConfig> tools) {
    return new GuardrailsEgressProperties(true, ViolationAction.DENY, allowed, tools);
  }

  @Test
  void shouldDenyEverythingWhenUsingDefaults() {
    // given / when
    GuardrailsEgressProperties defaults = new GuardrailsEgressProperties();

    // then
    assertTrue(defaults.enabled());
    assertEquals(ViolationAction.DENY, defaults.onViolation());
    assertEquals(List.of(), defaults.allowedDestinations());
    assertEquals(List.of(), defaults.tools());
  }

  @Test
  void shouldBuildAPolicyThatAllowsNothingWhenAllowlistIsEmpty() {
    // given: the fail-closed default must survive the trip through configuration
    EgressPolicy policy = new GuardrailsEgressProperties().toPolicy();

    // when / then
    assertFalse(policy.allows(Destination.of("api.github.com")));
  }

  @Test
  void shouldBuildTheDeclaredToolsWhenConfigured() {
    // given
    GuardrailsEgressProperties configured =
        properties(
            List.of("api.github.com"),
            List.of(
                new ToolConfig("http_get", List.of("url")),
                new ToolConfig("send_email", List.of("to", "cc"))));

    // when
    EgressPolicy policy = configured.toPolicy();

    // then
    assertEquals(
        List.of("http_get", "send_email"),
        policy.tools().stream().map(EgressTool::toolName).toList());
  }

  @Test
  void shouldBuildTheAllowlistWhenConfigured() {
    // given
    GuardrailsEgressProperties configured =
        properties(
            List.of("api.github.com", "*.internal.corp"),
            List.of(new ToolConfig("http_get", List.of("url"))));

    // when
    EgressPolicy policy = configured.toPolicy();

    // then
    assertTrue(policy.allows(Destination.of("a.internal.corp")));
    assertTrue(policy.allows(Destination.of("api.github.com")));
  }

  @Test
  void shouldUseEmptyListsWhenNothingIsConfigured() {
    // given: Spring binds a missing list as null
    GuardrailsEgressProperties bound =
        new GuardrailsEgressProperties(true, ViolationAction.DENY, null, null);

    // when / then
    assertEquals(List.of(), bound.allowedDestinations());
    assertEquals(List.of(), bound.tools());
  }

  @Test
  void shouldCopyAllowedDestinationsDefensivelyWhenConstructed() {
    // given
    List<String> mutable = new ArrayList<>(List.of("api.github.com"));
    GuardrailsEgressProperties configured = properties(mutable, List.of());

    // when
    mutable.add("api.evil.com");

    // then
    assertEquals(List.of("api.github.com"), configured.allowedDestinations());
  }

  @Test
  void shouldCopyToolsDefensivelyWhenConstructed() {
    // given
    List<ToolConfig> mutable = new ArrayList<>(List.of(new ToolConfig("http_get", List.of("url"))));
    GuardrailsEgressProperties configured = properties(List.of(), mutable);

    // when
    mutable.add(new ToolConfig("other", List.of("url")));

    // then
    assertEquals(1, configured.tools().size());
  }

  @Test
  void shouldFailWhenADeclaredToolHasNoDestinationArgument() {
    // given: the mistake must surface at startup, not as a silent fail-open at runtime
    GuardrailsEgressProperties configured =
        properties(List.of(), List.of(new ToolConfig("http_get", List.of())));

    // when / then
    assertThrows(IllegalArgumentException.class, configured::toPolicy);
  }

  @Test
  void shouldFailWhenAnAllowlistEntryIsMalformed() {
    // given
    GuardrailsEgressProperties configured = properties(List.of("example.*"), List.of());

    // when / then
    assertThrows(IllegalArgumentException.class, configured::toPolicy);
  }
}
