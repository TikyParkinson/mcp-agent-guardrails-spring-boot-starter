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

import io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port.out.ToolDefinitionCatalogPort;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.ToolDefinition;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory catalog of current tool definitions. The wiring layer (the starter, in its future
 * integration) calls {@link #register(ToolDefinition)} for every decorated tool at startup; {@code
 * register} is adapter API, deliberately not part of the port the use case sees.
 */
public final class InMemoryToolDefinitionCatalog implements ToolDefinitionCatalogPort {

  private final Map<String, ToolDefinition> definitions = new ConcurrentHashMap<>();

  /** Registers (or refreshes) the current definition of a tool. */
  public void register(ToolDefinition definition) {
    Objects.requireNonNull(definition, "definition");
    definitions.put(definition.toolName(), definition);
  }

  @Override
  public Optional<ToolDefinition> findByName(String toolName) {
    Objects.requireNonNull(toolName, "toolName");
    return Optional.ofNullable(definitions.get(toolName));
  }
}
