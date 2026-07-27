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
package io.github.tikyparkinson.mcpguardrails.injectionguard.domain;

import io.github.tikyparkinson.mcpguardrails.core.domain.ScanBudget;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure scanner: applies every rule to every string value found in the (recursively flattened)
 * argument map. Nesting deeper than {@link #MAX_DEPTH} is ignored to avoid nesting bombs;
 * non-string leaves (numbers, booleans, null) are not a text vector and are skipped.
 *
 * <p>The walk is bounded by a {@link ScanBudget}. Running out of it does not mean the arguments are
 * clean — it means part of them was never seen — so the result says so and the guardrail denies.
 */
public final class ArgumentScanner {

  private ArgumentScanner() {}

  /** Scans the arguments with the given rules, within the default budget. Never null. */
  public static ScanResult scan(Map<String, Object> arguments, List<InjectionRule> rules) {
    return scan(arguments, rules, ScanBudget.defaults());
  }

  /** Scans within the given budget, reporting whether the whole structure was reached. */
  public static ScanResult scan(
      Map<String, Object> arguments, List<InjectionRule> rules, ScanBudget budget) {
    Objects.requireNonNull(arguments, "arguments");
    Objects.requireNonNull(rules, "rules");
    Objects.requireNonNull(budget, "budget");
    Walk walk = new Walk(rules, budget);
    walk.scanMapEntries("", arguments, 0);
    return new ScanResult(walk.findings, walk.complete);
  }

  /** Carries the budget and whether it held, so the recursion does not have to thread them. */
  private static final class Walk {
    private final List<InjectionRule> rules;
    private final ScanBudget budget;
    private final List<ScanResult.Finding> findings = new ArrayList<>();
    private int visited;
    private boolean complete = true;

    private Walk(List<InjectionRule> rules, ScanBudget budget) {
      this.rules = rules;
      this.budget = budget;
    }

    /**
     * Asked once per element by the two loops below, which is every point where the walk can grow.
     * {@link #scanValue} does not repeat the question: nothing between a loop's check and its call
     * increases the count, so a second check there could never be true.
     */
    private boolean exhausted() {
      if (visited >= budget.maxNodes()) {
        complete = false;
        return true;
      }
      return false;
    }

    private void scanValue(String path, Object value, int depth) {
      if (depth > budget.maxDepth()) {
        complete = false;
        return;
      }
      switch (value) {
        case String text -> applyRules(path, text);
        case Map<?, ?> map -> scanMapEntries(path, map, depth);
        case List<?> list -> scanList(path, list, depth);
        case null, default -> {
          // non-string leaf: not a text vector
        }
      }
    }

    private void scanMapEntries(String path, Map<?, ?> map, int depth) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (exhausted()) {
          return;
        }
        String childPath =
            path.isEmpty() ? String.valueOf(entry.getKey()) : path + "." + entry.getKey();
        scanValue(childPath, entry.getValue(), depth + 1);
      }
    }

    private void scanList(String path, List<?> list, int depth) {
      for (int index = 0; index < list.size(); index++) {
        if (exhausted()) {
          return;
        }
        scanValue(path + "[" + index + "]", list.get(index), depth + 1);
      }
    }

    /**
     * Matched against the folded form so a rule written in ASCII still fires on an argument dressed
     * up with look-alike characters. The finding still names the argument as it arrived.
     */
    private void applyRules(String path, String text) {
      visited++;
      String normalized = TextNormalizer.normalize(text);
      for (InjectionRule rule : rules) {
        if (rule.matches(normalized)) {
          findings.add(new ScanResult.Finding(rule.id(), rule.severity(), path));
        }
      }
    }
  }
}
