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
package io.github.tikyparkinson.mcpguardrails.ratelimit.infrastructure;

import io.github.tikyparkinson.mcpguardrails.ratelimit.domain.RateLimitPolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Rate limit configuration, bound to the {@code mcp.guardrails.ratelimit} prefix.
 *
 * @param enabled whether the rate limit guardrail is registered. Default: {@code true}.
 * @param maxInvocations invocations allowed per window per (agent, tool). Default: {@code 60}.
 * @param window fixed window size. Default: {@code PT1M} (one minute).
 */
@ConfigurationProperties(prefix = "mcp.guardrails.ratelimit")
public record GuardrailsRatelimitProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("60") int maxInvocations,
    Duration window) {

  @ConstructorBinding
  public GuardrailsRatelimitProperties {
    window = window == null ? Duration.ofMinutes(1) : window;
  }

  /** Default configuration: enabled, 60 invocations per minute per (agent, tool). */
  public GuardrailsRatelimitProperties() {
    this(true, 60, Duration.ofMinutes(1));
  }

  /** Builds the immutable domain policy this configuration describes. */
  public RateLimitPolicy toPolicy() {
    return new RateLimitPolicy(maxInvocations, window);
  }
}
