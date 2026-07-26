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
package io.github.tikyparkinson.mcpguardrails.trifecta.adapter.in.chain;

import io.github.tikyparkinson.mcpguardrails.core.application.port.out.Guardrail;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.trifecta.application.port.in.AssessTrifectaUseCase;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.Capability;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.SessionId;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.TrifectaComplete;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.TrifectaIncomplete;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Trifecta guardrail: a session where private data, untrusted content and outbound communication
 * all meet is exploitable by construction, whether or not anybody is exploiting it.
 *
 * <p>Runs at order 90, after {@code anomaly-detector} (80) and before {@code ratelimit} (100). No
 * single guardrail can see this, because each judges one invocation and the trifecta is a property
 * of a session.
 *
 * <p>The outcome is only ever {@link Allow} or {@link Escalate}, never {@code Deny}. Plenty of
 * legitimate sessions meet all three — an assistant that reads a ticket, looks up a customer and
 * replies by email — so denying would break the product. Escalating puts a person in front of it,
 * and {@code approval-gate} turns that into a real pause.
 */
public final class TrifectaGuardrail implements Guardrail {

  public static final String GUARDRAIL_NAME = "trifecta-correlator";

  private final AssessTrifectaUseCase useCase;
  private final SessionIdResolver sessionIdResolver;

  public TrifectaGuardrail(AssessTrifectaUseCase useCase, SessionIdResolver sessionIdResolver) {
    this.useCase = Objects.requireNonNull(useCase, "useCase");
    this.sessionIdResolver = Objects.requireNonNull(sessionIdResolver, "sessionIdResolver");
  }

  @Override
  public String name() {
    return GUARDRAIL_NAME;
  }

  @Override
  public int order() {
    return 90;
  }

  /**
   * The invocation instant comes from the context rather than a clock read here, so session expiry
   * measures when the call happened and not how long the guardrails before this one took.
   */
  @Override
  public GuardrailDecision evaluate(ToolInvocationContext context) {
    Objects.requireNonNull(context, "context");
    SessionId sessionId = sessionIdResolver.resolve(context);
    return switch (useCase.assess(sessionId, context.toolName().value(), context.occurredAt())) {
      case TrifectaIncomplete _ -> new Allow();
      case TrifectaComplete complete -> new Escalate(describe(complete));
    };
  }

  private static String describe(TrifectaComplete complete) {
    String legs =
        Stream.of(Capability.values())
            .filter(complete.capabilities()::contains)
            .map(Capability::describe)
            .collect(Collectors.joining(", "));
    String closing = complete.closedNow() ? "; closed by this invocation" : "";
    return "lethal trifecta active in this session (" + legs + ")" + closing;
  }
}
