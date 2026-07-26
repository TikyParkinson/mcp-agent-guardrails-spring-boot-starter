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
import java.util.Map;
import java.util.Objects;

/**
 * Resolves a dotted path ({@code url}, {@code request.endpoint}) inside the invocation arguments.
 *
 * <p>A path pointing at a string yields one value; at a list of strings, all of them; anything else
 * — a missing key, a number, a nested structure — yields nothing, which the use case treats as an
 * unreadable destination.
 */
public final class ArgumentPathResolver {

  private ArgumentPathResolver() {}

  /** Values found at the given path. Never null; empty when the path leads nowhere usable. */
  public static List<String> resolve(Map<String, Object> arguments, String path) {
    Objects.requireNonNull(arguments, "arguments");
    Objects.requireNonNull(path, "path");
    Object current = arguments;
    for (String segment : path.split("\\.", -1)) {
      if (!(current instanceof Map<?, ?> map)) {
        return List.of();
      }
      current = map.get(segment);
    }
    return valuesOf(current);
  }

  private static List<String> valuesOf(Object value) {
    return switch (value) {
      case String text -> text.isBlank() ? List.of() : List.of(text);
      case List<?> list ->
          list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
      case null, default -> List.of();
    };
  }
}
