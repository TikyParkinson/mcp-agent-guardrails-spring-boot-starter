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
package io.github.tikyparkinson.mcpguardrails.egress.domain;

import java.util.List;
import java.util.Objects;

/**
 * A tool declared as capable of egress, and where its destination travels.
 *
 * <p>{@code destinationArguments} are paths into the invocation arguments ({@code url}, {@code
 * request.endpoint}, {@code recipients}). At least one is required: registering an egress tool with
 * no destination to check would be a silent fail-open.
 */
public record EgressTool(String toolName, List<String> destinationArguments) {

  public EgressTool {
    Objects.requireNonNull(toolName, "toolName");
    destinationArguments =
        List.copyOf(Objects.requireNonNull(destinationArguments, "destinationArguments"));
    if (toolName.isBlank()) {
      throw new IllegalArgumentException("toolName must not be blank");
    }
    if (destinationArguments.isEmpty()) {
      throw new IllegalArgumentException(
          "egress tool " + toolName + " must declare at least one destination argument");
    }
    if (destinationArguments.stream().anyMatch(String::isBlank)) {
      throw new IllegalArgumentException(
          "destination arguments of " + toolName + " must not be blank");
    }
  }
}
