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
package io.github.tikyparkinson.mcpguardrails.trifecta.domain;

import java.util.Objects;
import java.util.Set;

/**
 * Which legs a tool touches, as the operator declares them.
 *
 * <p>Declared rather than inferred on purpose: a tool's description is written by whoever publishes
 * the MCP server, which is precisely the vector {@code tool-integrity} exists to catch. A detector
 * whose input the attacker controls is not a detector.
 *
 * @param toolName tool as the MCP server exposes it
 * @param capabilities legs it touches; at least one, since declaring a tool with none is noise
 */
public record ToolCapabilities(String toolName, Set<Capability> capabilities) {

  public ToolCapabilities {
    Objects.requireNonNull(toolName, "toolName");
    if (toolName.isBlank()) {
      throw new IllegalArgumentException("toolName must not be blank");
    }
    capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
    if (capabilities.isEmpty()) {
      throw new IllegalArgumentException(
          "tool '" + toolName + "' declares no capability; leave it out instead");
    }
  }
}
