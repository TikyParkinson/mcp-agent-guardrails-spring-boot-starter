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
package io.github.tikyparkinson.mcpguardrails.toolintegrity.adapter.in.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.ToolDefinition;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpToolDefinitionMapperTest {

  @Test
  void shouldMapAllFieldsWhenToolFullyPopulated() {
    // given
    McpSchema.Tool tool =
        McpSchema.Tool.builder("search")
            .title("Search")
            .description("Searches the docs")
            .inputSchema(Map.of("type", "object"))
            .annotations(new McpSchema.ToolAnnotations("Search", true, false, true, null, null))
            .build();

    // when
    ToolDefinition definition = McpToolDefinitionMapper.from(tool);

    // then
    assertEquals("search", definition.toolName());
    assertEquals("Search", definition.title());
    assertEquals("Searches the docs", definition.description());
    assertEquals(Map.of("type", "object"), definition.inputSchema());
    // null hints are skipped: absent hint and null hint mean the same thing
    assertEquals(
        Map.of(
            "title",
            "Search",
            "readOnlyHint",
            true,
            "destructiveHint",
            false,
            "idempotentHint",
            true),
        definition.annotations());
  }

  @Test
  void shouldNormalizeAbsentFieldsWhenToolIsMinimal() {
    // given: only a name
    McpSchema.Tool tool = McpSchema.Tool.builder("bare").build();

    // when
    ToolDefinition definition = McpToolDefinitionMapper.from(tool);

    // then
    assertEquals("bare", definition.toolName());
    assertEquals("", definition.title());
    assertEquals("", definition.description());
    assertEquals(Map.of(), definition.outputSchema());
    assertEquals(Map.of(), definition.annotations());
  }

  @Test
  void shouldRejectNullToolWhenMapping() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> McpToolDefinitionMapper.from(null));
  }
}
