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
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Renders invocation arguments into a canonical, reproducible string so two calls can be compared
 * without keeping the arguments themselves.
 *
 * <p>Map keys are sorted explicitly: the iteration order of an immutable map is not stable across
 * JVMs, and an unstable rendering would make identical calls look different.
 *
 * <p>Two limits are worth knowing, both tolerable only because an anomaly escalates rather than
 * blocks. Values differing solely below {@link #MAX_DEPTH} render alike, so deeply nested paging
 * cursors can look like a repeated call. And a value whose {@code toString()} is the inherited
 * identity form renders differently per instance, hiding a real repetition; JSON-decoded arguments
 * never hit this, arbitrary objects passed straight into the port do.
 */
public final class CanonicalArguments {

  /**
   * Maximum nesting level rendered; deeper values collapse to an ellipsis. The bound also caps the
   * recursion below, so hostile nesting cannot overflow the stack.
   */
  public static final int MAX_DEPTH = 8;

  private CanonicalArguments() {}

  /** Renders the arguments. Never returns null. */
  public static String of(Map<String, Object> arguments) {
    Objects.requireNonNull(arguments, "arguments");
    StringBuilder out = new StringBuilder();
    renderValue(out, arguments, 1);
    return out.toString();
  }

  private static void renderValue(StringBuilder out, Object value, int depth) {
    if (depth > MAX_DEPTH) {
      out.append('…');
      return;
    }
    switch (value) {
      case Map<?, ?> map -> renderMap(out, map, depth);
      case List<?> list -> renderList(out, list, depth);
      case null -> out.append("null");
      default -> out.append(value);
    }
  }

  private static void renderMap(StringBuilder out, Map<?, ?> map, int depth) {
    TreeMap<String, Object> sorted = new TreeMap<>();
    map.forEach((key, mapValue) -> sorted.put(String.valueOf(key), mapValue));
    out.append('{');
    sorted.forEach(
        (key, mapValue) -> {
          out.append(key).append(':');
          renderValue(out, mapValue, depth + 1);
          out.append(';');
        });
    out.append('}');
  }

  private static void renderList(StringBuilder out, List<?> list, int depth) {
    out.append('[');
    for (Object element : list) {
      renderValue(out, element, depth + 1);
      out.append(';');
    }
    out.append(']');
  }
}
