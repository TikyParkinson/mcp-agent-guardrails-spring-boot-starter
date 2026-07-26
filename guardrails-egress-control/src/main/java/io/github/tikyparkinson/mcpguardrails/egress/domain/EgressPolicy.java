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
package io.github.tikyparkinson.mcpguardrails.egress.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The egress policy in force: which tools can reach the network and where they may go.
 *
 * <p>An empty allowlist denies every destination, which is the default posture of this guardrail.
 */
public record EgressPolicy(List<EgressTool> tools, List<AllowedDestination> allowedDestinations) {

  public EgressPolicy {
    tools = List.copyOf(Objects.requireNonNull(tools, "tools"));
    allowedDestinations =
        List.copyOf(Objects.requireNonNull(allowedDestinations, "allowedDestinations"));
  }

  /** The declaration of the given tool, if it was declared as egress capable. Never null. */
  public Optional<EgressTool> egressToolNamed(String toolName) {
    Objects.requireNonNull(toolName, "toolName");
    return tools.stream().filter(tool -> tool.toolName().equals(toolName)).findFirst();
  }

  /** True when at least one allowlist entry permits the destination. */
  public boolean allows(Destination destination) {
    Objects.requireNonNull(destination, "destination");
    return allowedDestinations.stream().anyMatch(allowed -> allowed.matches(destination));
  }
}
