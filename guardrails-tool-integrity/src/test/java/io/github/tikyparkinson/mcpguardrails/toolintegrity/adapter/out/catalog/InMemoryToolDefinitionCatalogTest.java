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
package io.github.tikyparkinson.mcpguardrails.toolintegrity.adapter.out.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.ToolDefinition;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InMemoryToolDefinitionCatalogTest {

  private static final ToolDefinition DEFINITION =
      new ToolDefinition("search", "", "v1", Map.of(), Map.of(), Map.of());
  private static final ToolDefinition UPDATED =
      new ToolDefinition("search", "", "v2", Map.of(), Map.of(), Map.of());

  private final InMemoryToolDefinitionCatalog catalog = new InMemoryToolDefinitionCatalog();

  @Test
  void shouldReturnRegisteredDefinitionWhenLookedUp() {
    // given
    catalog.register(DEFINITION);

    // when / then
    assertEquals(Optional.of(DEFINITION), catalog.findByName("search"));
  }

  @Test
  void shouldReturnEmptyWhenToolNotRegistered() {
    // given / when / then
    assertEquals(Optional.empty(), catalog.findByName("unknown"));
  }

  @Test
  void shouldExposeLatestDefinitionWhenReRegistered() {
    // given
    catalog.register(DEFINITION);

    // when: re-registration refreshes (this is what lets the guardrail see current state)
    catalog.register(UPDATED);

    // then
    assertEquals(Optional.of(UPDATED), catalog.findByName("search"));
  }

  @Test
  void shouldRejectNullInputsWhenInvoked() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> catalog.register(null));
    assertThrows(NullPointerException.class, () -> catalog.findByName(null));
  }
}
