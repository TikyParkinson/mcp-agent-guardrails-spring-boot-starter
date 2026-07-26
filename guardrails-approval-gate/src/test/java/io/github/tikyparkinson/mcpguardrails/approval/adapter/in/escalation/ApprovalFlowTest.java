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
package io.github.tikyparkinson.mcpguardrails.approval.adapter.in.escalation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.approval.adapter.out.channel.InMemoryApprovalRequestAdapter;
import io.github.tikyparkinson.mcpguardrails.approval.application.usecase.RequestApprovalService;
import io.github.tikyparkinson.mcpguardrails.approval.domain.ApprovalRequest;
import io.github.tikyparkinson.mcpguardrails.approval.domain.Approved;
import io.github.tikyparkinson.mcpguardrails.approval.domain.Rejected;
import io.github.tikyparkinson.mcpguardrails.approval.infrastructure.GuardrailsApprovalProperties;
import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.ApprovedExecution;
import io.github.tikyparkinson.mcpguardrails.core.domain.ChainVerdict;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.EscalationOutcome;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailEvaluation;
import io.github.tikyparkinson.mcpguardrails.core.domain.RejectedExecution;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** The gate wired to the real channel and service, as the starter would assemble it. */
class ApprovalFlowTest {

  private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");
  private static final String REASON = "anomalous agent behaviour";
  private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);

  @Test
  @Timeout(15)
  void shouldRunTheToolWhenAPersonApprovesWhileTheInvocationWaits() throws InterruptedException {
    // given an escalated invocation waiting for a decision
    RequestApprovalService service = service(Duration.ofSeconds(10), true);
    ApprovalGate gate = new ApprovalGate(service);
    AtomicReference<EscalationOutcome> outcome = new AtomicReference<>();
    CountDownLatch finished = new CountDownLatch(1);
    Thread.ofVirtual()
        .start(
            () -> {
              outcome.set(gate.resolve(context(), escalated()));
              finished.countDown();
            });
    waitUntilPending(service);

    // when a person approves the request they can see
    service.resolve(service.pendingApprovals().getFirst().id(), new Approved("alice"));

    // then the invocation is released with that approval
    assertTrue(finished.await(10, TimeUnit.SECONDS));
    assertEquals(new ApprovedExecution("alice"), outcome.get());
  }

  @Test
  @Timeout(15)
  void shouldShowTheApproverWhatIsAboutToRun() throws InterruptedException {
    // given an escalated invocation waiting
    RequestApprovalService service = service(Duration.ofSeconds(10), true);
    ApprovalGate gate = new ApprovalGate(service);
    Thread.ofVirtual().start(() -> gate.resolve(context(), escalated()));
    waitUntilPending(service);

    // when the human side lists what is pending
    ApprovalRequest pending = service.pendingApprovals().getFirst();

    // then it describes the invocation well enough to decide on: agent, tool, arguments and why
    assertEquals("agent-1", pending.agentId());
    assertEquals("delete_table", pending.toolName());
    assertEquals(Map.of("table", "prod"), pending.arguments());
    assertEquals(REASON, pending.reason());
  }

  @Test
  @Timeout(15)
  void shouldHideTheArgumentsWhenConfiguredTo() throws InterruptedException {
    // given a gate configured to keep arguments out of the channel
    RequestApprovalService service = service(Duration.ofSeconds(10), false);
    ApprovalGate gate = new ApprovalGate(service);
    Thread.ofVirtual().start(() -> gate.resolve(context(), escalated()));
    waitUntilPending(service);

    // when the human side lists what is pending
    // then the payload never reached the approval channel, which matters when that channel is
    // less protected than the invocation itself
    assertEquals(Map.of(), service.pendingApprovals().getFirst().arguments());
  }

  @Test
  @Timeout(15)
  void shouldRejectTheInvocationWhenAPersonRefuses() throws InterruptedException {
    // given an escalated invocation waiting
    RequestApprovalService service = service(Duration.ofSeconds(10), true);
    ApprovalGate gate = new ApprovalGate(service);
    AtomicReference<EscalationOutcome> outcome = new AtomicReference<>();
    CountDownLatch finished = new CountDownLatch(1);
    Thread.ofVirtual()
        .start(
            () -> {
              outcome.set(gate.resolve(context(), escalated()));
              finished.countDown();
            });
    waitUntilPending(service);

    // when a person refuses
    service.resolve(service.pendingApprovals().getFirst().id(), new Rejected("bob", "not on prod"));

    // then the invocation is blocked and the refusal is attributed
    assertTrue(finished.await(10, TimeUnit.SECONDS));
    assertEquals(new RejectedExecution("not on prod (by bob)"), outcome.get());
  }

  @Test
  @Timeout(15)
  void shouldRejectTheInvocationWhenNobodyAnswers() {
    // given a gate with a short deadline and nobody watching
    ApprovalGate gate = new ApprovalGate(service(SHORT_TIMEOUT, true));

    // when an escalated invocation arrives
    EscalationOutcome outcome = gate.resolve(context(), escalated());

    // then it is blocked: silence is the one answer that must never authorize
    RejectedExecution rejected = assertInstanceOf(RejectedExecution.class, outcome);
    assertTrue(rejected.reason().contains("no approval within"), rejected.reason());
  }

  @Test
  @Timeout(15)
  void shouldRejectImmediatelyWhenTheChannelIsFull() {
    // given a channel with a single slot already taken by a waiting invocation
    RequestApprovalService service =
        new RequestApprovalService(
            new InMemoryApprovalRequestAdapter(1, 1),
            new GuardrailsApprovalProperties(true, Duration.ofSeconds(30), 1, 1, true).toPolicy());
    ApprovalGate gate = new ApprovalGate(service);
    Thread.ofVirtual().start(() -> gate.resolve(context(), escalated()));
    waitUntilPending(service);

    // when a second invocation is escalated
    long startedAt = System.nanoTime();
    EscalationOutcome outcome = gate.resolve(context(), escalated());
    long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

    // then it is refused at once rather than queueing behind a full channel, which is what would
    // turn a saturated gate into an outage
    assertInstanceOf(RejectedExecution.class, outcome);
    assertTrue(elapsedMillis < 1_000, "waited " + elapsedMillis + "ms on a full channel");
  }

  @Test
  @Timeout(15)
  void shouldNeverApproveWithoutAPerson() {
    // given a gate whose channel holds a single slot and a short deadline
    ApprovalGate gate =
        new ApprovalGate(
            new RequestApprovalService(
                new InMemoryApprovalRequestAdapter(1, 1),
                new GuardrailsApprovalProperties(true, SHORT_TIMEOUT, 1, 1, true).toPolicy()));

    // when several escalations run through it with nobody deciding
    long approvals = 0;
    for (int attempt = 0; attempt < 3; attempt++) {
      if (gate.resolve(context(), escalated()) instanceof ApprovedExecution) {
        approvals++;
      }
    }

    // then none was ever approved: expiry and saturation both close the door
    assertEquals(0, approvals);
  }

  private static RequestApprovalService service(Duration timeout, boolean includeArguments) {
    GuardrailsApprovalProperties properties =
        new GuardrailsApprovalProperties(true, timeout, 20, 5, includeArguments);
    return new RequestApprovalService(
        new InMemoryApprovalRequestAdapter(
            properties.maxPending(), properties.maxPendingPerAgent()),
        properties.toPolicy());
  }

  /** Waits for the escalating thread to have published its request before acting on it. */
  private static void waitUntilPending(RequestApprovalService service) {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (service.pendingApprovals().isEmpty() && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
  }

  private static ToolInvocationContext context() {
    return new ToolInvocationContext(
        new AgentId("agent-1"),
        new ToolName("delete_table"),
        NOW,
        Map.of("table", "prod"),
        Map.of());
  }

  private static ChainVerdict escalated() {
    return new ChainVerdict(
        new Escalate(REASON),
        List.of(new GuardrailEvaluation("anomaly-detector", new Escalate(REASON))));
  }
}
