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
package io.github.tikyparkinson.mcpguardrails.anomaly.domain;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Everything the detector needs to know about an agent, in a single read.
 *
 * @param withinWindow invocations inside the analysis window, the current one included
 * @param toolsBeforeWindow tools the agent used before the window — its baseline
 * @param invocationsBeforeWindow how many invocations make up that baseline, so the heuristics can
 *     stay quiet until there is something to compare against
 */
public record AgentHistory(
    List<InvocationRecord> withinWindow,
    Set<String> toolsBeforeWindow,
    long invocationsBeforeWindow) {

  public AgentHistory {
    withinWindow = List.copyOf(Objects.requireNonNull(withinWindow, "withinWindow"));
    toolsBeforeWindow = Set.copyOf(Objects.requireNonNull(toolsBeforeWindow, "toolsBeforeWindow"));
    if (invocationsBeforeWindow < 0) {
      throw new IllegalArgumentException(
          "invocationsBeforeWindow must not be negative, was " + invocationsBeforeWindow);
    }
  }
}
