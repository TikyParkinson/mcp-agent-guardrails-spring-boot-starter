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

/** Applies every active pattern to every string reachable inside a structure. */
public final class SecretScanner {

  private SecretScanner() {}

  /**
   * Scans the values and reports what matched and where.
   *
   * @param locationPrefix prepended to every reported path, e.g. {@code "arguments"}
   */
  public static SecretScanResult scan(
      Map<String, Object> values, List<SecretPattern> patterns, String locationPrefix) {
    return scan(values, patterns, locationPrefix, ScanBudget.defaults());
  }

  /** Scans within the given budget, reporting whether the whole structure was reached. */
  public static SecretScanResult scan(
      Map<String, Object> values,
      List<SecretPattern> patterns,
      String locationPrefix,
      ScanBudget budget) {
    Objects.requireNonNull(patterns, "patterns");
    Objects.requireNonNull(locationPrefix, "locationPrefix");
    FlattenedArguments flattened = ValueFlattener.flatten(values, budget);
    List<SecretFinding> findings = new ArrayList<>();
    for (FlattenedValue value : flattened.values()) {
      String location = locationPrefix + "." + value.path();
      for (SecretPattern pattern : patterns) {
        if (pattern.pattern().matcher(value.text()).find()) {
          findings.add(new SecretFinding(pattern.id(), pattern.severity(), location));
        }
      }
    }
    return new SecretScanResult(findings, flattened.complete());
  }
}
