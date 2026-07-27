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
package io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port.out;

import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.ToolDefinition;
import java.util.Optional;

/**
 * Outbound port for the catalog of current tool definitions. The core invocation context does not
 * carry definitions, so the guardrail looks them up here; the wiring layer registers each decorated
 * tool's definition at startup.
 */
public interface ToolDefinitionCatalogPort {

  /** Current definition of the tool, if registered. Never null. */
  Optional<ToolDefinition> findByName(String toolName);
}
