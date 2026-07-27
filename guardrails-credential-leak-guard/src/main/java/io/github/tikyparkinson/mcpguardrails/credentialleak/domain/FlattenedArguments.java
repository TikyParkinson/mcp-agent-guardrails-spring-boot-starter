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
package io.github.tikyparkinson.mcpguardrails.credentialleak.domain;

import java.util.List;
import java.util.Objects;

/**
 * The values a walk found, and whether it managed to see all of them.
 *
 * <p>{@code complete == false} means precisely that parts of these arguments were never looked at.
 * It is not a warning to log: it is what makes the guardrail deny. Answering {@code Allow} after
 * running out of budget would claim the arguments are clean when the truth is that the scan
 * stopped.
 *
 * @param values what was reached, whether or not the walk finished
 * @param complete false when the walk ran out of nodes or depth
 */
public record FlattenedArguments(List<FlattenedValue> values, boolean complete) {

  public FlattenedArguments {
    values = List.copyOf(Objects.requireNonNull(values, "values"));
  }
}
