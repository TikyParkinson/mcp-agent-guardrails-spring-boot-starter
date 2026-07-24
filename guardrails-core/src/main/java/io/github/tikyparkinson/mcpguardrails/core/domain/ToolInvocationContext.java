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

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of one MCP tool invocation, as seen by the guardrail chain.
 *
 * <p>Arguments and metadata are defensively copied; the record never exposes mutable state.
 */
public record ToolInvocationContext(
    AgentId agentId,
    ToolName toolName,
    Instant occurredAt,
    Map<String, Object> arguments,
    Map<String, Object> metadata) {

  public ToolInvocationContext {
    Objects.requireNonNull(agentId, "agentId");
    Objects.requireNonNull(toolName, "toolName");
    Objects.requireNonNull(occurredAt, "occurredAt");
    arguments = Map.copyOf(Objects.requireNonNull(arguments, "arguments"));
    metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
  }
}
