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
package io.github.tikyparkinson.mcpguardrails.trifecta.infrastructure;

import io.github.tikyparkinson.mcpguardrails.trifecta.domain.Capability;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.ToolCapabilities;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.TrifectaPolicy;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Trifecta configuration, bound to the {@code mcp.guardrails.trifecta} prefix.
 *
 * @param enabled whether the guardrail is registered. Default: {@code true}.
 * @param sessionIdleTimeout how long without invocations before a session is forgotten. Default:
 *     {@code PT30M}.
 * @param sessionMaxDuration how long a session may live from its first invocation, however busy it
 *     is. Default: {@code PT2H}. Must be at least as long as {@code sessionIdleTimeout}. Both
 *     bounds are needed: every invocation refreshes the idle clock, so a busy agent would never
 *     reach it and a closed trifecta would keep escalating indefinitely.
 * @param tools tools declared as touching one or more legs of the trifecta. Default:
 *     <strong>empty</strong>, in which case this guardrail detects nothing at all — declared, not
 *     inferred, because a tool's description is written by whoever publishes the MCP server.
 */
@ConfigurationProperties(prefix = "mcp.guardrails.trifecta")
public record GuardrailsTrifectaProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("PT30M") Duration sessionIdleTimeout,
    @DefaultValue("PT2H") Duration sessionMaxDuration,
    List<ToolConfig> tools) {

  @ConstructorBinding
  public GuardrailsTrifectaProperties {
    tools = tools == null ? List.of() : List.copyOf(tools);
  }

  /** Default configuration: enabled, half-hour idle, two-hour lifetime, nothing declared. */
  public GuardrailsTrifectaProperties() {
    this(true, Duration.ofMinutes(30), Duration.ofHours(2), List.of());
  }

  /** Builds the policy this configuration describes. Validates the values on the way. */
  public TrifectaPolicy toPolicy() {
    List<ToolCapabilities> declared =
        tools.stream().map(tool -> new ToolCapabilities(tool.name(), tool.capabilities())).toList();
    return new TrifectaPolicy(declared, sessionIdleTimeout, sessionMaxDuration);
  }

  /**
   * One tool and the legs it touches.
   *
   * @param name tool name as the MCP server exposes it
   * @param capabilities legs it touches; at least one, so a tool declared with none is rejected
   *     with a message naming it rather than with a bare binding failure
   */
  public record ToolConfig(String name, Set<Capability> capabilities) {

    public ToolConfig {
      capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }
  }
}
