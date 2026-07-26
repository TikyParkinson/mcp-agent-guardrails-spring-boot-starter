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
import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalId;
import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalRequest;
import java.util.List;

/**
 * Inbound port for the human side. Whatever the operator exposes — a REST endpoint, a CLI, a chat
 * bot — talks to this.
 */
public interface ResolveApprovalUseCase {

  /** Requests still waiting, oldest first. Never null. */
  List<ApprovalRequest> pendingApprovals();

  /**
   * Records a person's decision. False when the request does not exist, already expired or was
   * already decided: the first decision wins and later ones do not overwrite it.
   */
  boolean resolve(ApprovalId id, ApprovalDecision decision);
}
