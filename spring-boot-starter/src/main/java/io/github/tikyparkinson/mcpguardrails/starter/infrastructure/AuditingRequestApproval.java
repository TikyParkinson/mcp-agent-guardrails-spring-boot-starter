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

import io.github.tikyparkinson.mcpguardrails.approval.application.port.in.RequestApprovalUseCase;
import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalDecision;
import io.github.tikyparkinson.mcpguardrails.approval.domain.Approved;
import io.github.tikyparkinson.mcpguardrails.approval.domain.Rejected;
import io.github.tikyparkinson.mcpguardrails.audit.application.port.in.RecordAuditEventUseCase;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEventType;
import io.github.tikyparkinson.mcpguardrails.audit.domain.NewAuditEvent;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records how an escalation ended: approved, rejected, or expired because nobody answered.
 *
 * <p>Who lifted a block and when is the most audit-worthy event a governance product has, and until
 * now it was nowhere in the log. A verdict-level auditor cannot capture it, because a human
 * decision is not a {@code GuardrailDecision}.
 *
 * <p>An expiry is recorded as its own outcome rather than as a rejection. It is a denial nobody
 * decided, and a review that cannot tell the two apart draws the wrong conclusion about whoever was
 * on duty. {@link Rejected#SYSTEM} already marks that difference in the approval domain; this class
 * only carries it across.
 *
 * <p>What it deliberately does not do is classify <em>why</em> the system rejected. {@code
 * Rejected.SYSTEM} covers both an expiry and a full queue, and those are different operational
 * problems — nobody was watching, versus the channel is saturated and the request never reached a
 * person. The domain does not distinguish them beyond the reason text, so the detail states that no
 * person was involved and lets the reason say the rest, instead of guessing from the wording and
 * labelling one as the other.
 */
public final class AuditingRequestApproval implements RequestApprovalUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(AuditingRequestApproval.class);

  private static final String EMITTED_BY = "approval-gate";

  private final RequestApprovalUseCase delegate;
  private final RecordAuditEventUseCase auditBus;

  public AuditingRequestApproval(
      RequestApprovalUseCase delegate, RecordAuditEventUseCase auditBus) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.auditBus = Objects.requireNonNull(auditBus, "auditBus");
  }

  @Override
  public ApprovalDecision requestApproval(
      String agentId,
      String toolName,
      Map<String, Object> arguments,
      String reason,
      Instant requestedAt) {
    ApprovalDecision decision =
        delegate.requestApproval(agentId, toolName, arguments, reason, requestedAt);
    publish(agentId, toolName, decision);
    return decision;
  }

  private void publish(String agentId, String toolName, ApprovalDecision decision) {
    try {
      auditBus.publish(
          new NewAuditEvent(
              agentId, toolName, EMITTED_BY, AuditEventType.APPROVAL_RESOLVED, describe(decision)));
    } catch (RuntimeException failure) {
      LOG.warn("could not audit the resolution of an escalation for tool '{}'", toolName, failure);
    }
  }

  private static String describe(ApprovalDecision decision) {
    return switch (decision) {
      case Approved approved -> "approved by " + approved.approver();
      case Rejected rejected when Rejected.SYSTEM.equals(rejected.approver()) ->
          "not approved, no person involved: " + rejected.reason();
      case Rejected rejected -> "rejected by " + rejected.approver() + ": " + rejected.reason();
    };
  }
}
