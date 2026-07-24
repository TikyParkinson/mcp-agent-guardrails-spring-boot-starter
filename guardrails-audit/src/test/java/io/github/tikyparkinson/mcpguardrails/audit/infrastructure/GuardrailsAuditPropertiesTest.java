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
package io.github.tikyparkinson.mcpguardrails.audit.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GuardrailsAuditPropertiesTest {

  @Test
  void shouldEnableWithDefaultBufferWhenDefaultConstructorUsed() {
    // given / when
    GuardrailsAuditProperties properties = new GuardrailsAuditProperties();

    // then
    assertTrue(properties.enabled());
    assertEquals(1000, properties.inMemoryMaxEvents());
  }

  @Test
  void shouldHonorExplicitValuesWhenProvided() {
    // given / when
    GuardrailsAuditProperties properties = new GuardrailsAuditProperties(false, 5);

    // then
    assertFalse(properties.enabled());
    assertEquals(5, properties.inMemoryMaxEvents());
  }
}
