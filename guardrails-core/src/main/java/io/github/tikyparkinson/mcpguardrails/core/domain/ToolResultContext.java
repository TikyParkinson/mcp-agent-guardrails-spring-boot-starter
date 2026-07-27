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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of the result of one MCP tool invocation, as seen by the outbound chain.
 *
 * <p>{@code textContents} holds the text of every textual content of the result, in order of
 * appearance, and is the only redactable part: a {@link Redact} must return a list of the same
 * size. {@code structuredContent} is exposed read-only so a guardrail can scan it and respond
 * {@link Block}; it is never rewritten.
 */
public record ToolResultContext(
    AgentId agentId,
    ToolName toolName,
    Instant occurredAt,
    List<String> textContents,
    Map<String, Object> structuredContent,
    boolean error) {

  public ToolResultContext {
    Objects.requireNonNull(agentId, "agentId");
    Objects.requireNonNull(toolName, "toolName");
    Objects.requireNonNull(occurredAt, "occurredAt");
    textContents = List.copyOf(Objects.requireNonNull(textContents, "textContents"));
    structuredContent = Map.copyOf(Objects.requireNonNull(structuredContent, "structuredContent"));
  }

  /**
   * Returns a copy with the textual contents replaced positionally.
   *
   * @throws IllegalArgumentException if {@code replacements} does not have the same size
   */
  public ToolResultContext withTextContents(List<String> replacements) {
    Objects.requireNonNull(replacements, "replacements");
    if (replacements.size() != textContents.size()) {
      throw new IllegalArgumentException(
          "replacements must have size %d, was %d"
              .formatted(textContents.size(), replacements.size()));
    }
    return new ToolResultContext(
        agentId, toolName, occurredAt, replacements, structuredContent, error);
  }
}
