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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.approval.application.port.in.RequestApprovalUseCase;
import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalDecision;
import io.github.tikyparkinson.mcpguardrails.approval.domain.Approved;
import io.github.tikyparkinson.mcpguardrails.approval.domain.Rejected;
import io.github.tikyparkinson.mcpguardrails.audit.application.port.in.RecordAuditEventUseCase;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEvent;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEventType;
import io.github.tikyparkinson.mcpguardrails.audit.domain.NewAuditEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Who lifted a block and when. Until this decorator existed it was nowhere in the audit log, which
 * for a governance product is the single most audit-worthy event there is.
 */
class AuditingRequestApprovalTest {

  private static final Instant WHEN = Instant.parse("2026-07-27T10:00:00Z");
  private static final String SECRET = "AKIAIOSFODNN7EXAMPLE";

  private final RecordingBus bus = new RecordingBus();

  @Test
  void shouldNameTheApproverWhenAPersonApproves() {
    // given a person who released the invocation
    // when the decorator records the outcome
    resolveWith(new Approved("alice"));

    // then the trail says who
    assertEquals("approved by alice", bus.events.get(0).detail());
  }

  @Test
  void shouldNameTheApproverAndReasonWhenAPersonRejects() {
    // given a person who refused, with a reason
    // when the decorator records the outcome
    resolveWith(new Rejected("bob", "not on production"));

    // then both reach the trail
    assertEquals("rejected by bob: not on production", bus.events.get(0).detail());
  }

  @Test
  void shouldSayNobodyDecidedWhenTheRequestExpires() {
    // given an escalation nobody answered within the deadline
    // when the decorator records the outcome
    resolveWith(Rejected.byTimeout(Duration.ofMinutes(2)));

    // then the trail distinguishes it from a decision somebody took. An expiry is a denial nobody
    // made, and reading it as deliberate draws the wrong conclusion about whoever was on duty
    assertEquals(
        "not approved, no person involved: no approval within PT2M", bus.events.get(0).detail());
  }

  @Test
  void shouldSayNobodyDecidedWhenTheQueueIsFull() {
    // given a channel already holding as many requests as it admits
    // when the decorator records the outcome
    resolveWith(Rejected.byQuota(20));

    // then it is not labelled as an expiry: the request never reached a person at all, which is a
    // different operational problem from nobody watching. The reason tells them apart
    assertTrue(bus.events.get(0).detail().startsWith("not approved, no person involved:"));
    assertTrue(bus.events.get(0).detail().contains("too many approvals pending"));
  }

  @Test
  void shouldUseTheSameEventTypeForEveryOutcomeWhenRecording() {
    // given the four ways an escalation can end
    resolveWith(new Approved("alice"));
    resolveWith(new Rejected("bob", "no"));
    resolveWith(Rejected.byTimeout(Duration.ofMinutes(2)));
    resolveWith(Rejected.byQuota(20));

    // then all four share a type and differ in the detail: they are the same fact — the escalation
    // ended — and what changes is who decided it
    assertEquals(
        List.of(
            AuditEventType.APPROVAL_RESOLVED,
            AuditEventType.APPROVAL_RESOLVED,
            AuditEventType.APPROVAL_RESOLVED,
            AuditEventType.APPROVAL_RESOLVED),
        bus.events.stream().map(NewAuditEvent::type).toList());
  }

  @Test
  void shouldAttributeTheEventToTheApprovalGateWhenRecording() {
    // given any outcome
    resolveWith(new Approved("alice"));

    // then the event is attributed to the module that held the invocation
    assertEquals("approval-gate", bus.events.get(0).emittedBy());
  }

  @Test
  void shouldReturnTheDecisionUntouchedWhenRecording() {
    // given a decision from the underlying use case
    ApprovalDecision original = new Approved("alice");

    // when the decorator runs
    ApprovalDecision returned =
        new AuditingRequestApproval((a, t, args, r, at) -> original, bus)
            .requestApproval("agent-1", "wire_transfer", Map.of(), "needs a human", WHEN);

    // then it is the very same decision: auditing observes, it never decides
    assertSame(original, returned);
  }

  @Test
  void shouldStillReturnTheDecisionWhenTheAuditBusFails() {
    // given an audit bus that is down
    RecordAuditEventUseCase broken =
        draft -> {
          throw new IllegalStateException("audit store down");
        };
    ApprovalDecision granted = new Approved("alice");

    // when the decorator runs
    ApprovalDecision returned =
        new AuditingRequestApproval((a, t, args, r, at) -> granted, broken)
            .requestApproval("agent-1", "wire_transfer", Map.of(), "needs a human", WHEN);

    // then the person's decision is still honoured
    assertSame(granted, returned);
  }

  @Test
  void shouldNeverRecordTheArgumentsWhenRecording() {
    // given an escalated invocation carrying a secret in its arguments
    new AuditingRequestApproval((a, t, args, r, at) -> new Approved("alice"), bus)
        .requestApproval("agent-1", "store_note", Map.of("text", SECRET), "needs a human", WHEN);

    // then the secret does not reach the audit log, even though the approval channel showed it to
    // a person: the two have different retention and different readers
    assertTrue(bus.events.get(0).detail().indexOf(SECRET) < 0);
  }

  @Test
  void shouldRejectNullCollaboratorsWhenConstructed() {
    // given
    RequestApprovalUseCase delegate = (a, t, args, r, at) -> new Approved("alice");

    // when / then
    assertThrows(NullPointerException.class, () -> new AuditingRequestApproval(null, bus));
    assertThrows(NullPointerException.class, () -> new AuditingRequestApproval(delegate, null));
  }

  private void resolveWith(ApprovalDecision decision) {
    new AuditingRequestApproval((a, t, args, r, at) -> decision, bus)
        .requestApproval("agent-1", "wire_transfer", Map.of(), "needs a human", WHEN);
  }

  /** Captures drafts so a test can assert on what would have been persisted. */
  private static final class RecordingBus implements RecordAuditEventUseCase {
    private final List<NewAuditEvent> events = new ArrayList<>();

    @Override
    public AuditEvent publish(NewAuditEvent draft) {
      events.add(draft);
      return new AuditEvent(
          UUID.randomUUID(),
          draft.agentId(),
          draft.toolName(),
          WHEN,
          draft.emittedBy(),
          draft.type(),
          draft.detail());
    }
  }
}
