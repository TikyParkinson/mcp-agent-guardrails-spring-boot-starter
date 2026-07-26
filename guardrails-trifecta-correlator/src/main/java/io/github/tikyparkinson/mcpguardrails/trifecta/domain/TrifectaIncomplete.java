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
 * Not every leg is present in this session yet.
 *
 * @param present what has been seen so far, kept so an operator can be told how close the session
 *     is without having to query anything else
 */
public record TrifectaIncomplete(Set<Capability> present) implements TrifectaVerdict {

  public TrifectaIncomplete {
    present = Set.copyOf(Objects.requireNonNull(present, "present"));
    if (present.size() == Capability.values().length) {
      throw new IllegalArgumentException(
          "every capability is present; this is a complete trifecta");
    }
  }
}
