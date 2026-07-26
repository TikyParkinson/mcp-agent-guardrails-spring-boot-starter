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
package io.github.tikyparkinson.mcpguardrails.egress.adapter.in.chain;

import io.github.tikyparkinson.mcpguardrails.core.application.port.out.Guardrail;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.egress.application.port.in.CheckEgressDestinationUseCase;
import io.github.tikyparkinson.mcpguardrails.egress.domain.Destination;
import io.github.tikyparkinson.mcpguardrails.egress.domain.DestinationsAllowed;
import io.github.tikyparkinson.mcpguardrails.egress.domain.EgressViolation;
import io.github.tikyparkinson.mcpguardrails.egress.domain.NotAnEgressTool;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Egress guardrail: a tool declared as network capable only runs when every destination it declares
 * is on the allowlist.
 *
 * <p>Runs at order 70, after {@code credential-leak} (60) and before {@code ratelimit} (100). A
 * tool that was not declared as egress capable is allowed: this guardrail has no opinion about it.
 * Reasons cite hosts and argument paths, never the raw argument value, which could carry a token in
 * its query string.
 */
public final class EgressGuardrail implements Guardrail {

  public static final String GUARDRAIL_NAME = "egress-control";

  /** Longest enumeration put in a reason before it is summarized. */
  static final int MAX_LISTED = 5;

  private final CheckEgressDestinationUseCase useCase;
  private final ViolationAction onViolation;

  public EgressGuardrail(CheckEgressDestinationUseCase useCase, ViolationAction onViolation) {
    this.useCase = Objects.requireNonNull(useCase, "useCase");
    this.onViolation = Objects.requireNonNull(onViolation, "onViolation");
  }

  @Override
  public String name() {
    return GUARDRAIL_NAME;
  }

  @Override
  public int order() {
    return 70;
  }

  @Override
  public GuardrailDecision evaluate(ToolInvocationContext context) {
    return switch (useCase.check(context.toolName().value(), context.arguments())) {
      case NotAnEgressTool _ -> new Allow();
      case DestinationsAllowed _ -> new Allow();
      case EgressViolation violation -> violationDecision(violation);
    };
  }

  private GuardrailDecision violationDecision(EgressViolation violation) {
    String reason = describe(violation);
    return switch (onViolation) {
      case DENY -> new Deny(reason);
      case ESCALATE -> new Escalate(reason);
    };
  }

  private static String describe(EgressViolation violation) {
    List<String> parts = new ArrayList<>(2);
    if (!violation.violations().isEmpty()) {
      List<String> hosts = violation.violations().stream().map(Destination::value).toList();
      parts.add("egress to a destination outside the allowlist (" + summarize(hosts) + ")");
    }
    if (!violation.undeterminedArguments().isEmpty()) {
      parts.add(
          "egress destination could not be determined from argument "
              + "("
              + summarize(violation.undeterminedArguments())
              + ")");
    }
    return String.join("; ", parts);
  }

  /** Deduplicates, keeps the first {@value #MAX_LISTED} entries and summarizes the rest. */
  private static String summarize(List<String> values) {
    List<String> distinct = values.stream().distinct().toList();
    String listed = distinct.stream().limit(MAX_LISTED).collect(Collectors.joining(", "));
    int remaining = distinct.size() - MAX_LISTED;
    return remaining > 0 ? listed + " and " + remaining + " more" : listed;
  }
}
