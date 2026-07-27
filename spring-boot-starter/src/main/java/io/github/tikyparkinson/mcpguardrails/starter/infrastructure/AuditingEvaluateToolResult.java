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

import io.github.tikyparkinson.mcpguardrails.audit.application.port.in.RecordAuditEventUseCase;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEventType;
import io.github.tikyparkinson.mcpguardrails.audit.domain.NewAuditEvent;
import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolResultUseCase;
import io.github.tikyparkinson.mcpguardrails.core.domain.Block;
import io.github.tikyparkinson.mcpguardrails.core.domain.PassThrough;
import io.github.tikyparkinson.mcpguardrails.core.domain.Redact;
import io.github.tikyparkinson.mcpguardrails.core.domain.ResultDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ResultEvaluation;
import io.github.tikyparkinson.mcpguardrails.core.domain.ResultVerdict;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolResultContext;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records every decision of the outbound chain on the audit bus, so a redaction leaves the same
 * kind of trace as a denial. Without it, a tool that returned secrets and had them stripped looks
 * in the log exactly like one that returned nothing worth stripping.
 *
 * <p>Only the reason of a {@link Redact} is recorded, never {@code sanitizedContents}. That field
 * holds the response text, and writing it here would put the very content the redaction just
 * removed into a store that usually has a different access policy than the tool output itself.
 */
public final class AuditingEvaluateToolResult implements EvaluateToolResultUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(AuditingEvaluateToolResult.class);

  private final EvaluateToolResultUseCase delegate;
  private final RecordAuditEventUseCase auditBus;

  public AuditingEvaluateToolResult(
      EvaluateToolResultUseCase delegate, RecordAuditEventUseCase auditBus) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.auditBus = Objects.requireNonNull(auditBus, "auditBus");
  }

  @Override
  public ResultVerdict evaluate(ToolResultContext context) {
    ResultVerdict verdict = delegate.evaluate(context);
    for (ResultEvaluation evaluation : verdict.evaluations()) {
      record(context, evaluation);
    }
    return verdict;
  }

  private void record(ToolResultContext context, ResultEvaluation evaluation) {
    try {
      auditBus.publish(
          new NewAuditEvent(
              context.agentId().value(),
              context.toolName().value(),
              evaluation.guardrailName(),
              typeOf(evaluation.decision()),
              reasonOf(evaluation.decision())));
    } catch (RuntimeException failure) {
      LOG.warn(
          "could not audit the result decision of guardrail '{}'",
          evaluation.guardrailName(),
          failure);
    }
  }

  private static AuditEventType typeOf(ResultDecision decision) {
    return switch (decision) {
      case PassThrough _ -> AuditEventType.RESULT_PASS_THROUGH;
      case Redact _ -> AuditEventType.RESULT_REDACTED;
      case Block _ -> AuditEventType.RESULT_BLOCKED;
    };
  }

  private static String reasonOf(ResultDecision decision) {
    return switch (decision) {
      case PassThrough passThrough -> passThrough.reason();
      case Redact redact -> redact.reason();
      case Block block -> block.reason();
    };
  }
}
