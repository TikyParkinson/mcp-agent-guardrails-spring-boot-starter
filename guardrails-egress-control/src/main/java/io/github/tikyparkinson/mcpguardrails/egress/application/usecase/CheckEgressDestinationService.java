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
package io.github.tikyparkinson.mcpguardrails.egress.application.usecase;

import io.github.tikyparkinson.mcpguardrails.egress.application.port.in.CheckEgressDestinationUseCase;
import io.github.tikyparkinson.mcpguardrails.egress.application.port.out.EgressPolicyPort;
import io.github.tikyparkinson.mcpguardrails.egress.domain.ArgumentPathResolver;
import io.github.tikyparkinson.mcpguardrails.egress.domain.Destination;
import io.github.tikyparkinson.mcpguardrails.egress.domain.DestinationExtractor;
import io.github.tikyparkinson.mcpguardrails.egress.domain.DestinationsAllowed;
import io.github.tikyparkinson.mcpguardrails.egress.domain.EgressCheckResult;
import io.github.tikyparkinson.mcpguardrails.egress.domain.EgressPolicy;
import io.github.tikyparkinson.mcpguardrails.egress.domain.EgressTool;
import io.github.tikyparkinson.mcpguardrails.egress.domain.EgressViolation;
import io.github.tikyparkinson.mcpguardrails.egress.domain.Extracted;
import io.github.tikyparkinson.mcpguardrails.egress.domain.NotAnEgressTool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Checks the destinations a declared egress tool is about to reach against the allowlist.
 *
 * <p>Fail closed on both counts: an unreadable destination is a violation, and a tool whose
 * declared arguments yield no destination at all does not proceed either.
 */
public final class CheckEgressDestinationService implements CheckEgressDestinationUseCase {

  private final EgressPolicyPort policyPort;

  public CheckEgressDestinationService(EgressPolicyPort policyPort) {
    this.policyPort = Objects.requireNonNull(policyPort, "policyPort");
  }

  @Override
  public EgressCheckResult check(String toolName, Map<String, Object> arguments) {
    Objects.requireNonNull(toolName, "toolName");
    Objects.requireNonNull(arguments, "arguments");
    EgressPolicy policy = policyPort.currentPolicy();
    return policy
        .egressToolNamed(toolName)
        .<EgressCheckResult>map(tool -> checkDeclaredTool(tool, arguments, policy))
        .orElseGet(NotAnEgressTool::new);
  }

  private EgressCheckResult checkDeclaredTool(
      EgressTool tool, Map<String, Object> arguments, EgressPolicy policy) {
    List<Destination> destinations = new ArrayList<>();
    List<String> undetermined = new ArrayList<>();
    for (String path : tool.destinationArguments()) {
      collectDestinations(arguments, path, destinations, undetermined);
    }
    if (!undetermined.isEmpty()) {
      return new EgressViolation(List.of(), List.copyOf(undetermined));
    }
    List<Destination> violations =
        destinations.stream().filter(destination -> !policy.allows(destination)).toList();
    return violations.isEmpty()
        ? new DestinationsAllowed(destinations)
        : new EgressViolation(violations, List.of());
  }

  /**
   * Every declared path contributes either a destination or an entry in {@code undetermined}, and a
   * tool always declares at least one path, so reaching this point with no destinations and nothing
   * undetermined is impossible.
   */
  private static void collectDestinations(
      Map<String, Object> arguments,
      String path,
      List<Destination> destinations,
      List<String> undetermined) {
    List<String> values = ArgumentPathResolver.resolve(arguments, path);
    if (values.isEmpty()) {
      undetermined.add(path);
      return;
    }
    for (String value : values) {
      if (DestinationExtractor.extract(value) instanceof Extracted(Destination destination)) {
        destinations.add(destination);
      } else {
        undetermined.add(path);
      }
    }
  }
}
