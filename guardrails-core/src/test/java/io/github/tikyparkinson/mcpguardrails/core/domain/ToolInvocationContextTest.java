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
package io.github.tikyparkinson.mcpguardrails.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolInvocationContextTest {

  private static final AgentId AGENT = new AgentId("agent-1");
  private static final ToolName TOOL = new ToolName("search");
  private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");

  @Test
  void shouldExposeDefensivelyCopiedArgumentsWhenSourceMapMutates() {
    // given
    Map<String, Object> arguments = new HashMap<>(Map.of("q", "hello"));
    ToolInvocationContext context =
        new ToolInvocationContext(AGENT, TOOL, NOW, arguments, Map.of());

    // when
    arguments.put("q", "tampered");

    // then
    assertEquals("hello", context.arguments().get("q"));
    Map<String, Object> exposed = context.arguments();
    assertThrows(UnsupportedOperationException.class, () -> exposed.put("x", "y"));
  }

  @Test
  void shouldRejectNullFieldsWhenConstructed() {
    // given / when / then
    assertThrows(
        NullPointerException.class,
        () -> new ToolInvocationContext(null, TOOL, NOW, Map.of(), Map.of()));
    assertThrows(
        NullPointerException.class,
        () -> new ToolInvocationContext(AGENT, null, NOW, Map.of(), Map.of()));
    assertThrows(
        NullPointerException.class,
        () -> new ToolInvocationContext(AGENT, TOOL, null, Map.of(), Map.of()));
    assertThrows(
        NullPointerException.class,
        () -> new ToolInvocationContext(AGENT, TOOL, NOW, null, Map.of()));
    assertThrows(
        NullPointerException.class,
        () -> new ToolInvocationContext(AGENT, TOOL, NOW, Map.of(), null));
  }

  @Test
  void shouldRejectBlankAgentIdWhenConstructed() {
    // given / when / then
    assertThrows(IllegalArgumentException.class, () -> new AgentId("  "));
    assertThrows(NullPointerException.class, () -> new AgentId(null));
  }

  @Test
  void shouldRejectBlankToolNameWhenConstructed() {
    // given / when / then
    assertThrows(IllegalArgumentException.class, () -> new ToolName(""));
    assertThrows(NullPointerException.class, () -> new ToolName(null));
  }
}
