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
package io.github.tikyparkinson.mcpguardrails.approval.application.port.in;

import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalDecision;
import java.time.Instant;
import java.util.Map;

/** Inbound port used by the escalation adapter when the chain has resolved {@code Escalate}. */
public interface RequestApprovalUseCase {

  /**
   * Puts the invocation in front of a person and waits. Never returns null, and never returns an
   * approval unless somebody granted it explicitly.
   */
  ApprovalDecision requestApproval(
      String agentId,
      String toolName,
      Map<String, Object> arguments,
      String reason,
      Instant requestedAt);
}
