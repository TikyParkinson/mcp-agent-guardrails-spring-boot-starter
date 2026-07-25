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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Flattens an arbitrary structure into (path, text) pairs so every string can be scanned, wherever
 * it is nested.
 *
 * <p>Recursion stops at depth {@value #MAX_DEPTH} to bound the cost of deeply nested input.
 * Non-string leaves (numbers, booleans, nulls) carry no credential and are skipped.
 */
public final class ValueFlattener {

  /** Maximum nesting level explored; deeper values are ignored. */
  public static final int MAX_DEPTH = 8;

  private ValueFlattener() {}

  /** Flattens the given map. Never returns null. */
  public static List<FlattenedValue> flatten(Map<String, Object> values) {
    Objects.requireNonNull(values, "values");
    List<FlattenedValue> flattened = new ArrayList<>();
    values.forEach((key, value) -> collect(String.valueOf(key), value, 1, flattened));
    return List.copyOf(flattened);
  }

  private static void collect(String path, Object value, int depth, List<FlattenedValue> sink) {
    if (depth > MAX_DEPTH) {
      return;
    }
    switch (value) {
      case String text -> sink.add(new FlattenedValue(path, text));
      case Map<?, ?> map ->
          map.forEach((key, nested) -> collect(path + "." + key, nested, depth + 1, sink));
      case List<?> list -> collectList(path, list, depth, sink);
      case null, default -> {
        // numbers, booleans and nulls cannot hold a credential
      }
    }
  }

  private static void collectList(String path, List<?> list, int depth, List<FlattenedValue> sink) {
    for (int index = 0; index < list.size(); index++) {
      collect(path + "[" + index + "]", list.get(index), depth + 1, sink);
    }
  }
}
