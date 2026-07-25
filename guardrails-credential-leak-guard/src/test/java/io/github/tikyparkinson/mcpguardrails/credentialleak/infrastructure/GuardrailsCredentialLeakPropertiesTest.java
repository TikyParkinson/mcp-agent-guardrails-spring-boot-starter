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
package io.github.tikyparkinson.mcpguardrails.credentialleak.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.credentialleak.adapter.in.chain.InputAction;
import io.github.tikyparkinson.mcpguardrails.credentialleak.adapter.in.chain.OutputAction;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretPattern;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretSeverity;
import io.github.tikyparkinson.mcpguardrails.credentialleak.infrastructure.GuardrailsCredentialLeakProperties.CustomPattern;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuardrailsCredentialLeakPropertiesTest {

  @Test
  void shouldStopCallsAndRedactResultsWhenUsingDefaults() {
    // given / when
    GuardrailsCredentialLeakProperties properties = new GuardrailsCredentialLeakProperties();

    // then
    assertTrue(properties.enabled());
    assertTrue(properties.builtInPatternsEnabled());
    assertEquals(InputAction.DENY, properties.onConfirmedInput());
    assertEquals(InputAction.ESCALATE, properties.onSuspectedInput());
    assertEquals(OutputAction.REDACT, properties.onOutputText());
    assertEquals(List.of(), properties.customPatterns());
  }

  @Test
  void shouldIncludeTheElevenBuiltInPatternsWhenTheyAreEnabled() {
    // given / when
    List<SecretPattern> patterns = new GuardrailsCredentialLeakProperties().toPatterns();

    // then
    assertEquals(11, patterns.size());
  }

  @Test
  void shouldDropTheBuiltInPatternsWhenTheyAreDisabled() {
    // given
    GuardrailsCredentialLeakProperties properties =
        new GuardrailsCredentialLeakProperties(
            true, false, InputAction.DENY, InputAction.ESCALATE, OutputAction.REDACT, List.of());

    // when / then
    assertEquals(List.of(), properties.toPatterns());
  }

  @Test
  void shouldAppendCustomPatternsAfterTheBuiltInOnesWhenBothArePresent() {
    // given
    GuardrailsCredentialLeakProperties properties =
        new GuardrailsCredentialLeakProperties(
            true,
            true,
            InputAction.DENY,
            InputAction.ESCALATE,
            OutputAction.REDACT,
            List.of(
                new CustomPattern("corp-token", "corp-[a-z0-9]{10}", SecretSeverity.CONFIRMED, 0)));

    // when
    List<SecretPattern> patterns = properties.toPatterns();

    // then
    assertEquals(12, patterns.size());
    assertEquals("corp-token", patterns.get(11).id());
  }

  @Test
  void shouldKeepTheSecretGroupOfACustomPatternWhenBuildingIt() {
    // given
    GuardrailsCredentialLeakProperties properties =
        new GuardrailsCredentialLeakProperties(
            true,
            false,
            InputAction.DENY,
            InputAction.ESCALATE,
            OutputAction.REDACT,
            List.of(new CustomPattern("keyed", "(key=)(\\w+)", SecretSeverity.SUSPECTED, 2)));

    // when / then
    assertEquals(2, properties.toPatterns().get(0).secretGroup());
  }

  @Test
  void shouldUseAnEmptyListWhenCustomPatternsAreNotConfigured() {
    // given: Spring binds a missing list as null
    GuardrailsCredentialLeakProperties properties =
        new GuardrailsCredentialLeakProperties(
            true, true, InputAction.DENY, InputAction.ESCALATE, OutputAction.REDACT, null);

    // when / then
    assertEquals(List.of(), properties.customPatterns());
  }

  @Test
  void shouldCopyCustomPatternsDefensivelyWhenConstructed() {
    // given
    List<CustomPattern> mutable =
        new ArrayList<>(List.of(new CustomPattern("a", "a", SecretSeverity.CONFIRMED, 0)));
    GuardrailsCredentialLeakProperties properties =
        new GuardrailsCredentialLeakProperties(
            true, true, InputAction.DENY, InputAction.ESCALATE, OutputAction.REDACT, mutable);

    // when
    mutable.add(new CustomPattern("b", "b", SecretSeverity.CONFIRMED, 0));

    // then
    assertEquals(1, properties.customPatterns().size());
  }

  @Test
  void shouldFailWhenACustomPatternHasAnInvalidRegex() {
    // given: a typo in configuration must fail loudly at startup, not silently disable detection
    GuardrailsCredentialLeakProperties properties =
        new GuardrailsCredentialLeakProperties(
            true,
            false,
            InputAction.DENY,
            InputAction.ESCALATE,
            OutputAction.REDACT,
            List.of(new CustomPattern("broken", "([unclosed", SecretSeverity.CONFIRMED, 0)));

    // when / then
    assertThrows(IllegalArgumentException.class, properties::toPatterns);
  }
}
