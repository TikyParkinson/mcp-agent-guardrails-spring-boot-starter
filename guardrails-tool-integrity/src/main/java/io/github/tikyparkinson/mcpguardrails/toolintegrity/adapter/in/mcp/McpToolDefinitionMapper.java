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

import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.ToolDefinition;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Converts an MCP SDK {@link McpSchema.Tool} into the domain's {@link ToolDefinition}. This is what
 * the wiring layer uses to register each decorated tool into the definition catalog.
 */
public final class McpToolDefinitionMapper {

  private McpToolDefinitionMapper() {}

  /** Maps the tool's public definition. Never null. */
  public static ToolDefinition from(McpSchema.Tool tool) {
    Objects.requireNonNull(tool, "tool");
    return new ToolDefinition(
        tool.name(),
        tool.title(),
        tool.description(),
        emptyIfNull(tool.inputSchema()),
        emptyIfNull(tool.outputSchema()),
        annotationsAsMap(tool.annotations()));
  }

  private static Map<String, Object> emptyIfNull(Map<String, Object> map) {
    return map == null ? Map.of() : map;
  }

  private static Map<String, Object> annotationsAsMap(McpSchema.ToolAnnotations annotations) {
    if (annotations == null) {
      return Map.of();
    }
    // HashMap tolerates the null values the SDK record allows; copyOf in the domain would not,
    // so nulls are skipped here (an absent hint and a null hint mean the same thing).
    Map<String, Object> map = new HashMap<>();
    putIfPresent(map, "title", annotations.title());
    putIfPresent(map, "readOnlyHint", annotations.readOnlyHint());
    putIfPresent(map, "destructiveHint", annotations.destructiveHint());
    putIfPresent(map, "idempotentHint", annotations.idempotentHint());
    putIfPresent(map, "openWorldHint", annotations.openWorldHint());
    putIfPresent(map, "returnDirect", annotations.returnDirect());
    return Map.copyOf(map);
  }

  private static void putIfPresent(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }
}
