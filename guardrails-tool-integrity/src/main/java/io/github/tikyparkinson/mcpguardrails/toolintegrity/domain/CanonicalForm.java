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
package io.github.tikyparkinson.mcpguardrails.toolintegrity.domain;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic canonical rendering of a {@link ToolDefinition}.
 *
 * <p>Java's immutable maps do not guarantee iteration order across processes, so map keys are
 * sorted explicitly — without this the fingerprint would not be reproducible between restarts and
 * the guardrail would trip on its own baselines. This is a domain rule, not an implementation
 * detail.
 */
public final class CanonicalForm {

  private CanonicalForm() {}

  /** Renders the definition into a canonical, reproducible string. */
  public static String render(ToolDefinition definition) {
    StringBuilder out = new StringBuilder();
    out.append("toolName=").append(definition.toolName()).append('\n');
    out.append("title=").append(definition.title()).append('\n');
    out.append("description=").append(definition.description()).append('\n');
    out.append("inputSchema=");
    renderValue(out, definition.inputSchema());
    out.append('\n');
    out.append("outputSchema=");
    renderValue(out, definition.outputSchema());
    out.append('\n');
    out.append("annotations=");
    renderValue(out, definition.annotations());
    return out.toString();
  }

  private static void renderValue(StringBuilder out, Object value) {
    switch (value) {
      case Map<?, ?> map -> renderMap(out, map);
      case List<?> list -> renderList(out, list);
      case null -> out.append("null");
      default -> out.append(value);
    }
  }

  private static void renderMap(StringBuilder out, Map<?, ?> map) {
    TreeMap<String, Object> sorted = new TreeMap<>();
    map.forEach((key, mapValue) -> sorted.put(String.valueOf(key), mapValue));
    out.append('{');
    sorted.forEach(
        (key, mapValue) -> {
          out.append(key).append(':');
          renderValue(out, mapValue);
          out.append(';');
        });
    out.append('}');
  }

  private static void renderList(StringBuilder out, List<?> list) {
    out.append('[');
    for (Object element : list) {
      renderValue(out, element);
      out.append(';');
    }
    out.append(']');
  }
}
