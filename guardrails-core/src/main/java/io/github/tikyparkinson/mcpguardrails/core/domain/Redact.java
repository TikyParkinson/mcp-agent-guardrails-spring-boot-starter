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

import java.util.List;
import java.util.Objects;

/**
 * The tool result is returned with its textual contents replaced.
 *
 * <p>{@code sanitizedContents} must have the same size as the inspected {@link
 * ToolResultContext#textContents()}: replacement is positional.
 */
public record Redact(List<String> sanitizedContents, String reason) implements ResultDecision {

  public Redact {
    sanitizedContents = List.copyOf(Objects.requireNonNull(sanitizedContents, "sanitizedContents"));
    Objects.requireNonNull(reason, "reason");
    if (reason.isBlank()) {
      throw new IllegalArgumentException("reason must not be blank");
    }
  }
}
