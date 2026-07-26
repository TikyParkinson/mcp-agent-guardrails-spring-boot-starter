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
package io.github.tikyparkinson.mcpguardrails.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EscalationOutcomeTest {

  @Test
  void shouldExposeOnlyTwoOutcomes() {
    // given the sealed outcome hierarchy
    // when its permitted subclasses are listed
    // then there are exactly two: the invocation either runs or it does not. A third, pending
    // case would push the waiting into the answer, where no caller could act on it
    assertEquals(2, EscalationOutcome.class.getPermittedSubclasses().length);
  }

  @Test
  void shouldCarryTheApproverWhenApproved() {
    // given an approval
    ApprovedExecution outcome = new ApprovedExecution("alice");

    // when read
    // then the decision is attributable
    assertEquals("alice", outcome.approvedBy());
    assertInstanceOf(EscalationOutcome.class, outcome);
  }

  @Test
  void shouldRejectAnApprovalWithoutAnApprover() {
    // given a blank approver
    // when constructed
    // then it fails: an approval nobody signed is not an approval
    assertThrows(IllegalArgumentException.class, () -> new ApprovedExecution(" "));
  }

  @Test
  void shouldRejectANullApprover() {
    // given no approver
    // when constructed
    // then it fails
    assertThrows(NullPointerException.class, () -> new ApprovedExecution(null));
  }

  @Test
  void shouldCarryTheReasonWhenRejected() {
    // given a rejection
    RejectedExecution outcome = new RejectedExecution("no approval within PT2M");

    // when read
    // then the motive travels to the agent
    assertEquals("no approval within PT2M", outcome.reason());
    assertInstanceOf(EscalationOutcome.class, outcome);
  }

  @Test
  void shouldRejectARejectionWithoutAReason() {
    // given a blank reason
    // when constructed
    // then it fails: expiry, refusal and an unreachable channel all land in this type, so the
    // reason is the only thing that tells them apart
    assertThrows(IllegalArgumentException.class, () -> new RejectedExecution(""));
  }

  @Test
  void shouldRejectANullReason() {
    // given no reason
    // when constructed
    // then it fails
    assertThrows(NullPointerException.class, () -> new RejectedExecution(null));
  }
}
