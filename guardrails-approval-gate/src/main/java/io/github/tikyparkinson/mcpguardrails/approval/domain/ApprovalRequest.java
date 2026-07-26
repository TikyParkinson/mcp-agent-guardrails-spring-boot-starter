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
package io.github.tikyparkinson.mcpguardrails.approval.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * What is put in front of a person to decide.
 *
 * <p>Carries the arguments because without them nobody can decide with any judgement: approving
 * {@code delete_table} without knowing which table is not approving, it is signing blank. The
 * consequence is that the approval channel sees whatever the invocation carries, secrets included,
 * and therefore inherits its protection level — which is why the policy can leave them out.
 *
 * @param id opaque identifier an approver presents to resolve this request
 * @param agentId agent that made the invocation
 * @param toolName tool it wants to run
 * @param arguments invocation arguments, empty when the policy excludes them
 * @param reason why the chain escalated, as the guardrails explained it
 * @param requestedAt instant of the invocation, taken from its context
 */
public record ApprovalRequest(
    ApprovalId id,
    String agentId,
    String toolName,
    Map<String, Object> arguments,
    String reason,
    Instant requestedAt) {

  public ApprovalRequest {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(requestedAt, "requestedAt");
    arguments = Map.copyOf(Objects.requireNonNull(arguments, "arguments"));
    requireNotBlank(agentId, "agentId");
    requireNotBlank(toolName, "toolName");
    requireNotBlank(reason, "reason");
  }

  private static void requireNotBlank(String value, String field) {
    Objects.requireNonNull(value, field);
    if (value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
