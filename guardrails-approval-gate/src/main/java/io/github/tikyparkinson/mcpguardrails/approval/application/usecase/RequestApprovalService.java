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
package io.github.tikyparkinson.mcpguardrails.approval.application.usecase;

import io.github.tikyparkinson.mcpguardrails.approval.application.port.in.RequestApprovalUseCase;
import io.github.tikyparkinson.mcpguardrails.approval.application.port.in.ResolveApprovalUseCase;
import io.github.tikyparkinson.mcpguardrails.approval.application.port.out.ApprovalRequestPort;
import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalDecision;
import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalId;
import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalPolicy;
import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalRequest;
import io.github.tikyparkinson.mcpguardrails.approval.domain.Rejected;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Holds an escalated invocation until somebody decides, or until the deadline decides for them.
 *
 * <p>No path here produces an approval on its own: {@code Approved} can only come back from the
 * channel, put there by a person. Saturation and expiry both end in {@code Rejected}. That is the
 * whole point of the module and it should be readable straight off the method, not inferred.
 */
public final class RequestApprovalService
    implements RequestApprovalUseCase, ResolveApprovalUseCase {

  private final ApprovalRequestPort approvalPort;
  private final ApprovalPolicy policy;

  public RequestApprovalService(ApprovalRequestPort approvalPort, ApprovalPolicy policy) {
    this.approvalPort = Objects.requireNonNull(approvalPort, "approvalPort");
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  @Override
  public ApprovalDecision requestApproval(
      String agentId,
      String toolName,
      Map<String, Object> arguments,
      String reason,
      Instant requestedAt) {
    Objects.requireNonNull(arguments, "arguments");
    ApprovalRequest request =
        new ApprovalRequest(
            ApprovalId.newId(),
            agentId,
            toolName,
            policy.includeArguments() ? arguments : Map.of(),
            reason,
            requestedAt);
    if (!approvalPort.submit(request)) {
      return Rejected.byQuota(policy.maxPending());
    }
    return approvalPort
        .awaitDecision(request.id(), policy.timeout())
        .orElseGet(() -> Rejected.byTimeout(policy.timeout()));
  }

  @Override
  public List<ApprovalRequest> pendingApprovals() {
    return approvalPort.pending();
  }

  @Override
  public boolean resolve(ApprovalId id, ApprovalDecision decision) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(decision, "decision");
    return approvalPort.resolve(id, decision);
  }
}
