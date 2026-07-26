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
package io.github.tikyparkinson.mcpguardrails.anomaly.infrastructure;

import io.github.tikyparkinson.mcpguardrails.anomaly.domain.AnomalyPolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Anomaly configuration, bound to the {@code mcp.guardrails.anomaly} prefix.
 *
 * @param enabled whether the anomaly guardrail is registered. Default: {@code true}.
 * @param window how far back each analysis looks. Default: {@code PT1M}.
 * @param repeatThreshold identical calls — same tool, same arguments — that report a loop. Default:
 *     {@code 5}.
 * @param novelToolThreshold never-before-seen tools within the window that report a sweep. Default:
 *     {@code 3}.
 * @param baselineMinInvocations invocations an agent must have made before the sweep heuristic
 *     speaks at all. Default: {@code 20}, so a cold start does not report every tool as new.
 * @param retention how long the default adapter keeps records and baseline. Default: {@code PT30M}.
 *     Must be at least as long as {@code window}, or the analysis would read a history that was
 *     already thrown away.
 * @param maxRecordsPerAgent cap on detailed records per agent in the default adapter. Default:
 *     {@code 500}. Past it the oldest records fold into the baseline summary instead of vanishing.
 */
@ConfigurationProperties(prefix = "mcp.guardrails.anomaly")
public record GuardrailsAnomalyProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("PT1M") Duration window,
    @DefaultValue("5") int repeatThreshold,
    @DefaultValue("3") int novelToolThreshold,
    @DefaultValue("20") long baselineMinInvocations,
    @DefaultValue("PT30M") Duration retention,
    @DefaultValue("500") int maxRecordsPerAgent) {

  @ConstructorBinding
  public GuardrailsAnomalyProperties {
    if (retention != null && window != null && retention.compareTo(window) < 0) {
      throw new IllegalArgumentException(
          "retention (" + retention + ") must be at least as long as window (" + window + ")");
    }
  }

  /** Default configuration, as documented above. */
  public GuardrailsAnomalyProperties() {
    this(true, Duration.ofMinutes(1), 5, 3, 20L, Duration.ofMinutes(30), 500);
  }

  /** Builds the policy the heuristics run under. Validates thresholds on the way. */
  public AnomalyPolicy toPolicy() {
    return new AnomalyPolicy(window, repeatThreshold, novelToolThreshold, baselineMinInvocations);
  }
}
