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
package io.github.tikyparkinson.mcpguardrails.starter.infrastructure;

import io.github.tikyparkinson.mcpguardrails.audit.adapter.in.chain.AuditGuardrail;
import io.github.tikyparkinson.mcpguardrails.audit.application.port.in.RecordAuditEventUseCase;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEventType;
import io.github.tikyparkinson.mcpguardrails.audit.domain.NewAuditEvent;
import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolInvocationUseCase;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.ChainVerdict;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailEvaluation;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records every guardrail decision of the inbound chain on the audit bus.
 *
 * <p>This is the bridge ARCHITECTURE.md §5 asks for: no guardrail module depends on {@code
 * guardrails-audit}, and the wiring layer — which legitimately depends on all of them — observes
 * the {@link ChainVerdict} once and writes the whole trace. A guardrail contributed by the operator
 * is covered by the same mechanism, without knowing that auditing exists.
 *
 * <p>The events carry the deciding guardrail as {@code emittedBy}, not this class: whoever reads
 * the trail wants to know who decided, and that the recording happens elsewhere is an
 * implementation detail that must not leak into the log.
 */
public final class AuditingEvaluateToolInvocation implements EvaluateToolInvocationUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(AuditingEvaluateToolInvocation.class);

  private final EvaluateToolInvocationUseCase delegate;
  private final RecordAuditEventUseCase auditBus;

  public AuditingEvaluateToolInvocation(
      EvaluateToolInvocationUseCase delegate, RecordAuditEventUseCase auditBus) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.auditBus = Objects.requireNonNull(auditBus, "auditBus");
  }

  @Override
  public ChainVerdict evaluate(ToolInvocationContext context) {
    ChainVerdict verdict = delegate.evaluate(context);
    for (GuardrailEvaluation evaluation : verdict.evaluations()) {
      publish(context, evaluation);
    }
    return verdict;
  }

  private void publish(ToolInvocationContext context, GuardrailEvaluation evaluation) {
    if (AuditGuardrail.GUARDRAIL_NAME.equals(evaluation.guardrailName())) {
      return;
    }
    try {
      auditBus.publish(
          new NewAuditEvent(
              context.agentId().value(),
              context.toolName().value(),
              evaluation.guardrailName(),
              typeOf(evaluation.decision()),
              reasonOf(evaluation.decision())));
    } catch (RuntimeException failure) {
      // A broken audit bus degrades observability, never protection: turning an allowed call into
      // an error here would make the audit store a single point of failure for the whole server.
      LOG.warn(
          "could not audit the decision of guardrail '{}'", evaluation.guardrailName(), failure);
    }
  }

  private static AuditEventType typeOf(GuardrailDecision decision) {
    return switch (decision) {
      case Allow _ -> AuditEventType.DECISION_ALLOW;
      case Deny _ -> AuditEventType.DECISION_DENY;
      case Escalate _ -> AuditEventType.DECISION_ESCALATE;
    };
  }

  private static String reasonOf(GuardrailDecision decision) {
    return switch (decision) {
      case Allow allow -> allow.reason();
      case Deny deny -> deny.reason();
      case Escalate escalate -> escalate.reason();
    };
  }
}
