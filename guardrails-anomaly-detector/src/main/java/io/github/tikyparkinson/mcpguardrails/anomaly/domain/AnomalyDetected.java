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
import java.util.Objects;

/** At least one heuristic fired. A verdict of anomaly without a signal does not exist. */
public record AnomalyDetected(List<AnomalySignal> signals) implements AnomalyVerdict {

  public AnomalyDetected {
    signals = List.copyOf(Objects.requireNonNull(signals, "signals"));
    if (signals.isEmpty()) {
      throw new IllegalArgumentException("an anomaly verdict must carry at least one signal");
    }
  }
}
