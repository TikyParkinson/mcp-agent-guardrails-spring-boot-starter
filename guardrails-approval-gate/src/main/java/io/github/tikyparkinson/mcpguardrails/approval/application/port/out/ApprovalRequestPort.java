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
package io.github.tikyparkinson.mcpguardrails.approval.application.port.out;

import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalDecision;
import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalId;
import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalRequest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The approval channel: where requests wait and how they are woken up. Replace it to move approvals
 * somewhere shared, or to a different medium altogether.
 *
 * <p>Four methods because there are two sides. Whoever asks uses {@link #submit} and {@link
 * #awaitDecision}; whoever decides uses {@link #pending} and {@link #resolve}. A single {@code
 * requestApproval} method would push onto every implementation the job of inventing how requests
 * are listed and woken, which is precisely what has to be replaceable.
 */
public interface ApprovalRequestPort {

  /**
   * Publishes the request so somebody can see it. False when the channel is saturated; the use case
   * turns that into a rejection, never into permission.
   */
  boolean submit(ApprovalRequest request);

  /**
   * Waits for the request to be decided, for at most the given time. An empty result means the
   * deadline passed with no answer.
   *
   * <p>Implementations must not hold a lock while waiting: the wait lasts as long as the timeout,
   * and serializing it would let one pending invocation block every other.
   */
  Optional<ApprovalDecision> awaitDecision(ApprovalId id, Duration timeout);

  /** Records the decision and wakes whoever waits. False when there was nothing to resolve. */
  boolean resolve(ApprovalId id, ApprovalDecision decision);

  /** Requests still waiting, oldest first. Never null. */
  List<ApprovalRequest> pending();
}
