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
package io.github.tikyparkinson.mcpguardrails.toolintegrity.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Normalized snapshot of a tool's public definition — everything a model reads to decide how to use
 * the tool, and therefore everything a poisoning attack would touch.
 *
 * <p>Absent title/description normalize to {@code ""}; the three maps are defensively copied and
 * may be empty.
 */
public record ToolDefinition(
    String toolName,
    String title,
    String description,
    Map<String, Object> inputSchema,
    Map<String, Object> outputSchema,
    Map<String, Object> annotations) {

  public ToolDefinition {
    Objects.requireNonNull(toolName, "toolName");
    if (toolName.isBlank()) {
      throw new IllegalArgumentException("toolName must not be blank");
    }
    title = title == null ? "" : title;
    description = description == null ? "" : description;
    inputSchema = Map.copyOf(Objects.requireNonNull(inputSchema, "inputSchema"));
    outputSchema = Map.copyOf(Objects.requireNonNull(outputSchema, "outputSchema"));
    annotations = Map.copyOf(Objects.requireNonNull(annotations, "annotations"));
  }
}
