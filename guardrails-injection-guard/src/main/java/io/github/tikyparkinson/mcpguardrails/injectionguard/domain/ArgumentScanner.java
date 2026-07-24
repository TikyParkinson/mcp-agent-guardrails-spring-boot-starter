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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure scanner: applies every rule to every string value found in the (recursively flattened)
 * argument map. Nesting deeper than {@link #MAX_DEPTH} is ignored to avoid nesting bombs;
 * non-string leaves (numbers, booleans, null) are not a text vector and are skipped.
 */
public final class ArgumentScanner {

  static final int MAX_DEPTH = 8;

  private ArgumentScanner() {}

  /** Scans the arguments with the given rules. Never null. */
  public static ScanResult scan(Map<String, Object> arguments, List<InjectionRule> rules) {
    Objects.requireNonNull(arguments, "arguments");
    Objects.requireNonNull(rules, "rules");
    List<ScanResult.Finding> findings = new ArrayList<>();
    for (Map.Entry<String, Object> entry : arguments.entrySet()) {
      scanValue(entry.getKey(), entry.getValue(), rules, findings, 1);
    }
    return new ScanResult(findings);
  }

  private static void scanValue(
      String path,
      Object value,
      List<InjectionRule> rules,
      List<ScanResult.Finding> findings,
      int depth) {
    if (depth > MAX_DEPTH) {
      return;
    }
    switch (value) {
      case String text -> applyRules(path, text, rules, findings);
      case Map<?, ?> map -> scanMap(path, map, rules, findings, depth);
      case List<?> list -> scanList(path, list, rules, findings, depth);
      case null, default -> {
        // non-string leaf: not a text vector
      }
    }
  }

  private static void scanMap(
      String path,
      Map<?, ?> map,
      List<InjectionRule> rules,
      List<ScanResult.Finding> findings,
      int depth) {
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      scanValue(path + "." + entry.getKey(), entry.getValue(), rules, findings, depth + 1);
    }
  }

  private static void scanList(
      String path,
      List<?> list,
      List<InjectionRule> rules,
      List<ScanResult.Finding> findings,
      int depth) {
    for (int i = 0; i < list.size(); i++) {
      scanValue(path + "[" + i + "]", list.get(i), rules, findings, depth + 1);
    }
  }

  private static void applyRules(
      String path, String text, List<InjectionRule> rules, List<ScanResult.Finding> findings) {
    for (InjectionRule rule : rules) {
      if (rule.matches(text)) {
        findings.add(new ScanResult.Finding(rule.id(), rule.severity(), path));
      }
    }
  }
}
