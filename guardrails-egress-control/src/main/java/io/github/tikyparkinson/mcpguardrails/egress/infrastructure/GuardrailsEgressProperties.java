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
package io.github.tikyparkinson.mcpguardrails.egress.infrastructure;

import io.github.tikyparkinson.mcpguardrails.egress.adapter.in.chain.ViolationAction;
import io.github.tikyparkinson.mcpguardrails.egress.domain.AllowedDestination;
import io.github.tikyparkinson.mcpguardrails.egress.domain.EgressPolicy;
import io.github.tikyparkinson.mcpguardrails.egress.domain.EgressTool;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Egress configuration, bound to the {@code mcp.guardrails.egress} prefix.
 *
 * @param enabled whether the egress guardrail is registered. Default: {@code true}.
 * @param onViolation action for a destination outside the allowlist or one that cannot be read.
 *     Default: {@code DENY}. There is no {@code ALLOW} option on purpose.
 * @param allowedDestinations hosts or {@code *.domain} patterns that may be reached. Default:
 *     <strong>empty</strong>, which denies every egress until the operator says otherwise.
 * @param tools tools declared as egress capable and where their destination travels. Default:
 *     empty, in which case this guardrail allows everything because nothing was declared.
 */
@ConfigurationProperties(prefix = "mcp.guardrails.egress")
public record GuardrailsEgressProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("DENY") ViolationAction onViolation,
    List<String> allowedDestinations,
    List<ToolConfig> tools) {

  @ConstructorBinding
  public GuardrailsEgressProperties {
    allowedDestinations =
        allowedDestinations == null ? List.of() : List.copyOf(allowedDestinations);
    tools = tools == null ? List.of() : List.copyOf(tools);
  }

  /** Default configuration: enabled, deny on violation, nothing allowed, nothing declared. */
  public GuardrailsEgressProperties() {
    this(true, ViolationAction.DENY, List.of(), List.of());
  }

  /** Builds the policy this configuration describes. */
  public EgressPolicy toPolicy() {
    List<EgressTool> egressTools =
        tools.stream()
            .map(tool -> new EgressTool(tool.name(), tool.destinationArguments()))
            .toList();
    List<AllowedDestination> allowed =
        allowedDestinations.stream().map(AllowedDestination::of).toList();
    return new EgressPolicy(egressTools, allowed);
  }

  /**
   * One tool declared as egress capable.
   *
   * @param name tool name as the MCP server exposes it.
   * @param destinationArguments dotted paths into the arguments that carry the destination ({@code
   *     url}, {@code request.endpoint}, {@code recipients}). At least one is required.
   */
  public record ToolConfig(String name, List<String> destinationArguments) {}
}
