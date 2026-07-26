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
package io.github.tikyparkinson.mcpguardrails.approval.infrastructure;

import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalPolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Approval gate configuration, bound to the {@code mcp.guardrails.approval} prefix.
 *
 * @param enabled whether the gate is registered. Default: {@code true}. Without it an escalation
 *     returns an error to the agent, as it did before this module existed.
 * @param timeout how long an invocation is held waiting for an answer. Default: {@code PT2M}. Must
 *     be shorter than the MCP client's own timeout, or the client gives up on a call whose approver
 *     can no longer reach anyone.
 * @param maxPending requests admitted at once. Default: {@code 20}. Every wait holds a server
 *     thread, so this must stay well below the pool; raise it only on virtual threads.
 * @param maxPendingPerAgent cap per agent. Default: {@code 5}. Stops one looping agent from filling
 *     the global quota and leaving every other agent without a channel. Must be ≤ {@code
 *     maxPending}.
 * @param includeArguments whether the invocation arguments travel in the request. Default: {@code
 *     true}, because approving without seeing them is signing blank. Turn it off when the approval
 *     channel is less protected than the invocation itself.
 */
@ConfigurationProperties(prefix = "mcp.guardrails.approval")
public record GuardrailsApprovalProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("PT2M") Duration timeout,
    @DefaultValue("20") int maxPending,
    @DefaultValue("5") int maxPendingPerAgent,
    @DefaultValue("true") boolean includeArguments) {

  @ConstructorBinding
  public GuardrailsApprovalProperties {}

  /** Default configuration, as documented above. */
  public GuardrailsApprovalProperties() {
    this(true, Duration.ofMinutes(2), 20, 5, true);
  }

  /** Builds the policy the gate runs under. Validates the values on the way. */
  public ApprovalPolicy toPolicy() {
    return new ApprovalPolicy(timeout, maxPending, maxPendingPerAgent, includeArguments);
  }
}
