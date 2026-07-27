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
 * The three legs meet in this session: the agent is exploitable by construction, whether or not
 * anybody is exploiting it.
 *
 * @param capabilities the three legs; anything else is not a complete trifecta
 * @param closedNow whether this invocation is what closed the triangle, or it was already closed.
 *     Both escalate — the difference is only what the reason tells the person reading it
 */
public record TrifectaComplete(Set<Capability> capabilities, boolean closedNow)
    implements TrifectaVerdict {

  public TrifectaComplete {
    capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
    if (capabilities.size() != Capability.values().length) {
      throw new IllegalArgumentException(
          "a complete trifecta needs every capability, got " + capabilities);
    }
  }
}
