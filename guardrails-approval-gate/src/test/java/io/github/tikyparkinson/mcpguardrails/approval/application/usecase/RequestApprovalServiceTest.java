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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.approval.application.port.out.ApprovalRequestPort;
import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalDecision;
import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalId;
import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalPolicy;
import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalRequest;
import io.github.tikyparkinson.mcpguardrails.approval.domain.Approved;
import io.github.tikyparkinson.mcpguardrails.approval.domain.Rejected;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RequestApprovalServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");
  private static final Duration TIMEOUT = Duration.ofMinutes(2);
  private static final Map<String, Object> ARGUMENTS = Map.of("table", "prod");
  private static final ApprovalPolicy POLICY = new ApprovalPolicy(TIMEOUT, 20, 5, true);

  private ApprovalRequestPort approvalPort;
  private RequestApprovalService service;

  @BeforeEach
  void setUp() {
    approvalPort = mock(ApprovalRequestPort.class);
    when(approvalPort.submit(any())).thenReturn(true);
    when(approvalPort.awaitDecision(any(), any())).thenReturn(Optional.empty());
    service = new RequestApprovalService(approvalPort, POLICY);
  }

  @Test
  void shouldReturnTheApprovalWhenSomebodyGrantsIt() {
    // given a channel where a person approved
    when(approvalPort.awaitDecision(any(), any())).thenReturn(Optional.of(new Approved("alice")));

    // when approval is requested
    ApprovalDecision decision = requestApproval();

    // then that decision comes back untouched
    assertEquals(new Approved("alice"), decision);
  }

  @Test
  void shouldReturnTheRejectionWhenSomebodyRefuses() {
    // given a channel where a person refused
    when(approvalPort.awaitDecision(any(), any()))
        .thenReturn(Optional.of(new Rejected("bob", "not on prod")));

    // when approval is requested
    ApprovalDecision decision = requestApproval();

    // then the refusal and its author come back untouched
    assertEquals(new Rejected("bob", "not on prod"), decision);
  }

  @Test
  void shouldRejectWhenNobodyAnswersWithinTheDeadline() {
    // given a channel where the wait expired
    when(approvalPort.awaitDecision(any(), any())).thenReturn(Optional.empty());

    // when approval is requested
    ApprovalDecision decision = requestApproval();

    // then silence denies: this is the fail-closed guarantee of the module
    Rejected rejected = assertInstanceOf(Rejected.class, decision);
    assertEquals("no approval within PT2M", rejected.reason());
  }

  @Test
  void shouldRejectWithoutWaitingWhenTheChannelIsSaturated() {
    // given a channel that admits nothing more
    when(approvalPort.submit(any())).thenReturn(false);

    // when approval is requested
    ApprovalDecision decision = requestApproval();

    // then it is refused immediately and no thread is parked: queuing behind a full channel is
    // what turns a saturated gate into an outage
    assertInstanceOf(Rejected.class, decision);
    verify(approvalPort, never()).awaitDecision(any(), any());
  }

  @Test
  void shouldNameTheGlobalLimitWhenRejectingBySaturation() {
    // given a saturated channel
    when(approvalPort.submit(any())).thenReturn(false);

    // when approval is requested
    Rejected rejected = assertInstanceOf(Rejected.class, requestApproval());

    // then the operator can see which limit to raise
    assertTrue(rejected.reason().contains("20"), rejected.reason());
  }

  @Test
  void shouldWaitExactlyTheConfiguredTimeout() {
    // given the configured deadline
    // when approval is requested
    requestApproval();

    // then the channel is asked to wait that long, not some other value
    verify(approvalPort).awaitDecision(any(), eq(TIMEOUT));
  }

  @Test
  void shouldSubmitTheInvocationDetailsForAPersonToRead() {
    // given an escalated invocation
    // when approval is requested
    requestApproval();

    // then the request carries what a person needs to decide
    ApprovalRequest submitted = captureSubmitted();
    assertEquals("agent-1", submitted.agentId());
    assertEquals("delete_table", submitted.toolName());
    assertEquals("anomalous agent behaviour", submitted.reason());
    assertEquals(NOW, submitted.requestedAt());
  }

  @Test
  void shouldIncludeTheArgumentsWhenThePolicyAllowsIt() {
    // given the default policy
    // when approval is requested
    requestApproval();

    // then the approver sees what is about to run: approving without them is signing blank
    assertEquals(ARGUMENTS, captureSubmitted().arguments());
  }

  @Test
  void shouldOmitTheArgumentsWhenThePolicyExcludesThem() {
    // given a policy that keeps arguments out of the channel
    service = new RequestApprovalService(approvalPort, new ApprovalPolicy(TIMEOUT, 20, 5, false));

    // when approval is requested
    requestApproval();

    // then nothing of the invocation payload reaches the channel
    assertEquals(Map.of(), captureSubmitted().arguments());
  }

  @Test
  void shouldWaitForTheRequestItJustSubmitted() {
    // given an escalated invocation
    // when approval is requested
    requestApproval();

    // then it waits on that request's own identifier, not on some other one
    ArgumentCaptor<ApprovalId> awaited = ArgumentCaptor.forClass(ApprovalId.class);
    verify(approvalPort).awaitDecision(awaited.capture(), any());
    assertEquals(captureSubmitted().id(), awaited.getValue());
  }

  @Test
  void shouldGiveEachRequestItsOwnIdentifier() {
    // given two escalated invocations
    requestApproval();
    requestApproval();

    // when the submitted requests are compared
    ArgumentCaptor<ApprovalRequest> submitted = ArgumentCaptor.forClass(ApprovalRequest.class);
    verify(approvalPort, times(2)).submit(submitted.capture());

    // then they do not share an identifier: one decision must never resolve two invocations
    assertEquals(2, submitted.getAllValues().stream().map(ApprovalRequest::id).distinct().count());
  }

  @Test
  void shouldListWhatTheChannelHasPending() {
    // given a channel holding one request
    ApprovalRequest waiting =
        new ApprovalRequest(ApprovalId.newId(), "agent-1", "tool", Map.of(), "why", NOW);
    when(approvalPort.pending()).thenReturn(List.of(waiting));

    // when the human side asks
    // then it sees the channel's own view
    assertEquals(List.of(waiting), service.pendingApprovals());
  }

  @Test
  void shouldPassAHumanDecisionToTheChannel() {
    // given a decision from a person
    ApprovalId id = ApprovalId.newId();
    when(approvalPort.resolve(id, new Approved("alice"))).thenReturn(true);

    // when it is recorded
    // then the channel confirms it took effect
    assertTrue(service.resolve(id, new Approved("alice")));
  }

  @Test
  void shouldReportWhenAHumanDecisionArrivesTooLate() {
    // given a request that is no longer waiting
    when(approvalPort.resolve(any(), any())).thenReturn(false);

    // when a decision is recorded
    // then the caller learns it changed nothing, rather than believing it worked
    assertFalse(service.resolve(ApprovalId.newId(), new Approved("alice")));
  }

  @Test
  void shouldRejectResolvingWithoutAnIdentifier() {
    // given no identifier
    // when a decision is recorded
    // then it fails
    assertThrows(NullPointerException.class, () -> service.resolve(null, new Approved("alice")));
  }

  @Test
  void shouldRejectResolvingWithoutADecision() {
    // given no decision
    // when recorded
    // then it fails
    ApprovalId id = ApprovalId.newId();
    assertThrows(NullPointerException.class, () -> service.resolve(id, null));
  }

  @Test
  void shouldRejectARequestWithoutArguments() {
    // given no argument map
    // when approval is requested
    // then it fails rather than quietly meaning "none shown"
    assertThrows(
        NullPointerException.class,
        () -> service.requestApproval("agent-1", "tool", null, "why", NOW));
  }

  @Test
  void shouldRejectAServiceWithoutAChannel() {
    // given no channel
    // when the service is built
    // then it fails at wiring time rather than on the first escalation
    assertThrows(NullPointerException.class, () -> new RequestApprovalService(null, POLICY));
  }

  @Test
  void shouldRejectAServiceWithoutAPolicy() {
    // given no policy
    // when the service is built
    // then it fails
    assertThrows(NullPointerException.class, () -> new RequestApprovalService(approvalPort, null));
  }

  @Test
  void shouldNeverApproveOnItsOwn() {
    // given every path that does not involve a person granting approval
    List<Runnable> pathsWithoutAnApprover =
        List.of(
            () -> when(approvalPort.submit(any())).thenReturn(false),
            () -> when(approvalPort.awaitDecision(any(), any())).thenReturn(Optional.empty()),
            () ->
                when(approvalPort.awaitDecision(any(), any()))
                    .thenReturn(Optional.of(new Rejected("bob", "no"))));

    // when each is exercised
    // then none produces an approval: this is the property the whole module exists for
    for (Runnable path : pathsWithoutAnApprover) {
      setUp();
      path.run();
      assertInstanceOf(Rejected.class, requestApproval());
    }
  }

  private ApprovalDecision requestApproval() {
    return service.requestApproval(
        "agent-1", "delete_table", ARGUMENTS, "anomalous agent behaviour", NOW);
  }

  private ApprovalRequest captureSubmitted() {
    ArgumentCaptor<ApprovalRequest> captor = ArgumentCaptor.forClass(ApprovalRequest.class);
    verify(approvalPort, atLeastOnce()).submit(captor.capture());
    return captor.getValue();
  }
}
