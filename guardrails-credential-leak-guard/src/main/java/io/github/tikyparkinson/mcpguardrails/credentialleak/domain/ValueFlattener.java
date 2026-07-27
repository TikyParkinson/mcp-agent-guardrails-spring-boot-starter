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
 * <p>The walk is bounded by a {@link ScanBudget}. Running out of it does not mean the arguments are
 * clean — it means part of them was never seen — so the result says so and the guardrail denies.
 * Non-string leaves (numbers, booleans, nulls) carry no credential and are skipped.
 *
 * <p>Map keys are scanned as well as map values: a credential used as a field name is still a
 * credential sitting in the arguments. Text that turns out to be Base64 is emitted twice, once as
 * it arrived and once decoded, so a {@code .env} serialized into an argument does not sail past
 * every pattern.
 */
public final class ValueFlattener {

  private ValueFlattener() {}

  /** Flattens the given map within the default budget. Never returns null. */
  public static FlattenedArguments flatten(Map<String, Object> values) {
    return flatten(values, ScanBudget.defaults());
  }

  /** Flattens the given map, stopping when the budget runs out. Never returns null. */
  public static FlattenedArguments flatten(Map<String, Object> values, ScanBudget budget) {
    Objects.requireNonNull(values, "values");
    Objects.requireNonNull(budget, "budget");
    Walk walk = new Walk(budget);
    walk.collectMap("", values, 0);
    return new FlattenedArguments(walk.sink, walk.complete);
  }

  /** Carries the budget and whether it held, so the recursion does not have to thread them. */
  private static final class Walk {
    private final ScanBudget budget;
    private final List<FlattenedValue> sink = new ArrayList<>();
    private boolean complete = true;

    private Walk(ScanBudget budget) {
      this.budget = budget;
    }

    private boolean exhausted() {
      if (sink.size() >= budget.maxNodes()) {
        complete = false;
        return true;
      }
      return false;
    }

    private void add(FlattenedValue value) {
      sink.add(value);
    }

    private void collect(String path, Object value, int depth) {
      if (depth > budget.maxDepth()) {
        complete = false;
        return;
      }
      if (exhausted()) {
        return;
      }
      switch (value) {
        case String text -> collectText(path, text);
        case Map<?, ?> map -> collectMap(path, map, depth);
        case List<?> list -> collectList(path, list, depth);
        case null, default -> {
          // numbers, booleans and nulls cannot hold a credential
        }
      }
    }

    /**
     * Emits the text itself and, when it turns out to be Base64, whatever it was hiding. The {@code
     * (base64)} suffix is the only trace that decoding happened — the decoded text is scanned and
     * discarded, never recorded, exactly like the original.
     */
    private void collectText(String path, String text) {
      add(new FlattenedValue(path, text));
      Base64Decoder.decode(text)
          .ifPresent(decoded -> add(new FlattenedValue(path + "(base64)", decoded)));
    }

    /**
     * Walks a map by value and by key. A credential used as a field name is still a credential in
     * the arguments, and until F-5 only the value side was looked at. The braces tell an
     * investigator that the secret was the name of the field rather than its contents, which are
     * different incidents.
     */
    private void collectMap(String path, Map<?, ?> map, int depth) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (exhausted()) {
          return;
        }
        Object key = entry.getKey();
        String childPath = path.isEmpty() ? String.valueOf(key) : path + "." + key;
        if (key instanceof String text) {
          collectText(path + "{" + text + "}", text);
        }
        collect(childPath, entry.getValue(), depth + 1);
      }
    }

    private void collectList(String path, List<?> list, int depth) {
      for (int index = 0; index < list.size(); index++) {
        if (exhausted()) {
          return;
        }
        collect(path + "[" + index + "]", list.get(index), depth + 1);
      }
    }
  }
}
