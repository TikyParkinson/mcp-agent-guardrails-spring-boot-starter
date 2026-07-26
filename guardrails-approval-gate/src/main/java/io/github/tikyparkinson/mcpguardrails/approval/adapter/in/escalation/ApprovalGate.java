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
package io.github.tikyparkinson.mcpguardrails.approval.adapter.in.escalation;

import io.github.tikyparkinson.mcpguardrails.approval.application.port.in.RequestApprovalUseCase;
import io.github.tikyparkinson.mcpguardrails.approval.domain.Approved;
import io.github.tikyparkinson.mcpguardrails.approval.domain.Rejected;
import io.github.tikyparkinson.mcpguardrails.core.application.port.out.EscalationResolver;
import io.github.tikyparkinson.mcpguardrails.core.domain.ApprovedExecution;
import io.github.tikyparkinson.mcpguardrails.core.domain.ChainVerdict;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.EscalationOutcome;
import io.github.tikyparkinson.mcpguardrails.core.domain.RejectedExecution;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import java.util.Objects;

/**
 * Turns an escalated verdict into a real pause: the invocation waits here until a person decides,
 * or until the deadline decides for them.
 *
 * <p>This is not a {@code Guardrail} and has no position in the chain. It runs once, after the
 * whole chain has already settled on {@code Escalate} — a guardrail could not do this job, since
 * the decision combiner keeps the first escalation and nothing running later can lower it back to
 * {@code Allow}.
 */
public final class ApprovalGate implements EscalationResolver {

  private final RequestApprovalUseCase useCase;

  public ApprovalGate(RequestApprovalUseCase useCase) {
    this.useCase = Objects.requireNonNull(useCase, "useCase");
  }

  /**
   * The invocation instant comes from the context rather than a clock read here, so the request a
   * person sees is dated when the call happened, not when the escalation reached this point.
   */
  @Override
  public EscalationOutcome resolve(ToolInvocationContext context, ChainVerdict verdict) {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(verdict, "verdict");
    return switch (useCase.requestApproval(
        context.agentId().value(),
        context.toolName().value(),
        context.arguments(),
        reasonOf(verdict),
        context.occurredAt())) {
      case Approved(String approver) -> new ApprovedExecution(approver);
      case Rejected(String approver, String reason) ->
          new RejectedExecution(reason + " (by " + approver + ")");
    };
  }

  /**
   * A resolver is only reached on an escalated verdict, so any other decision here means the
   * handler wired it wrong; failing loudly beats inventing a motive for a person to read.
   */
  private static String reasonOf(ChainVerdict verdict) {
    if (verdict.finalDecision() instanceof Escalate(String reason)) {
      return reason;
    }
    throw new IllegalArgumentException(
        "approval gate reached with a non-escalated verdict: " + verdict.finalDecision());
  }
}
