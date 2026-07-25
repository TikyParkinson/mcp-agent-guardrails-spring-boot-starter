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
package io.github.tikyparkinson.mcpguardrails.toolintegrity.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.toolintegrity.adapter.in.chain.MismatchAction;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.adapter.in.chain.UnknownDefinitionAction;
import org.junit.jupiter.api.Test;

class GuardrailsToolIntegrityPropertiesTest {

  @Test
  void shouldEnableWithDenyAndAllowDefaultsWhenDefaultConstructorUsed() {
    // given / when
    GuardrailsToolIntegrityProperties properties = new GuardrailsToolIntegrityProperties();

    // then
    assertTrue(properties.enabled());
    assertEquals(MismatchAction.DENY, properties.onMismatch());
    assertEquals(UnknownDefinitionAction.ALLOW, properties.onUnknownDefinition());
  }

  @Test
  void shouldHonorExplicitValuesWhenProvided() {
    // given / when
    GuardrailsToolIntegrityProperties properties =
        new GuardrailsToolIntegrityProperties(
            false, MismatchAction.ESCALATE, UnknownDefinitionAction.DENY);

    // then
    assertEquals(MismatchAction.ESCALATE, properties.onMismatch());
    assertEquals(UnknownDefinitionAction.DENY, properties.onUnknownDefinition());
  }
}
