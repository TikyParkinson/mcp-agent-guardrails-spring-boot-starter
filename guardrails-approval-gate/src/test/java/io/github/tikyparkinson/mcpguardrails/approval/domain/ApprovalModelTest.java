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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApprovalModelTest {

  private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");
  private static final Duration TWO_MINUTES = Duration.ofMinutes(2);
  private static final Duration BACKWARDS = Duration.ofMinutes(-1);

  @Test
  void shouldGenerateADifferentIdentifierEveryTime() {
    // given many generated identifiers
    Set<String> generated = new HashSet<>();
    for (int index = 0; index < 1_000; index++) {
      generated.add(ApprovalId.newId().value());
    }

    // when counted
    // then none repeats: an identifier is what an approver presents to decide, so a collision
    // would let one decision land on somebody else's invocation
    assertEquals(1_000, generated.size());
  }

  @Test
  void shouldRejectABlankIdentifier() {
    // given a blank value
    // when constructed
    // then it fails
    assertThrows(IllegalArgumentException.class, () -> new ApprovalId(" "));
  }

  @Test
  void shouldRejectANullIdentifier() {
    // given no value
    // when constructed
    // then it fails
    assertThrows(NullPointerException.class, () -> new ApprovalId(null));
  }

  @Test
  void shouldCopyTheArgumentsDefensively() {
    // given a mutable map handed to a request
    Map<String, Object> arguments = new HashMap<>(Map.of("table", "prod"));
    ApprovalRequest request = request(arguments);

    // when the caller mutates its own map afterwards
    arguments.put("table", "staging");

    // then what the approver sees is unchanged: the request must describe the invocation as it
    // was when it was escalated
    assertEquals(Map.of("table", "prod"), request.arguments());
  }

  @Test
  void shouldAcceptARequestWithoutArguments() {
    // given a policy that leaves arguments out
    ApprovalRequest request = request(Map.of());

    // when constructed
    // then an empty map is valid: it means "not shown", not "missing"
    assertEquals(Map.of(), request.arguments());
  }

  @Test
  void shouldRejectARequestWithoutAnAgent() {
    // given a blank agent
    // when constructed
    // then it fails: quotas are counted per agent
    ApprovalId id = ApprovalId.newId();
    assertThrows(
        IllegalArgumentException.class,
        () -> new ApprovalRequest(id, " ", "tool", Map.of(), "why", NOW));
  }

  @Test
  void shouldRejectARequestWithoutATool() {
    // given a blank tool name
    // when constructed
    // then it fails
    ApprovalId id = ApprovalId.newId();
    assertThrows(
        IllegalArgumentException.class,
        () -> new ApprovalRequest(id, "agent", "", Map.of(), "why", NOW));
  }

  @Test
  void shouldRejectARequestWithoutAReason() {
    // given a blank reason
    // when constructed
    // then it fails: a person asked to decide with no motive cannot decide
    ApprovalId id = ApprovalId.newId();
    assertThrows(
        IllegalArgumentException.class,
        () -> new ApprovalRequest(id, "agent", "tool", Map.of(), " ", NOW));
  }

  @Test
  void shouldRejectARequestWithoutAnInstant() {
    // given no instant
    // when constructed
    // then it fails
    ApprovalId id = ApprovalId.newId();
    assertThrows(
        NullPointerException.class,
        () -> new ApprovalRequest(id, "agent", "tool", Map.of(), "why", null));
  }

  @Test
  void shouldRejectARequestWithoutAnIdentifier() {
    // given no identifier
    // when constructed
    // then it fails
    assertThrows(
        NullPointerException.class,
        () -> new ApprovalRequest(null, "agent", "tool", Map.of(), "why", NOW));
  }

  @Test
  void shouldRejectARequestWithNullArguments() {
    // given no argument map at all
    // when constructed
    // then it fails rather than silently meaning "none shown"
    ApprovalId id = ApprovalId.newId();
    assertThrows(
        NullPointerException.class,
        () -> new ApprovalRequest(id, "agent", "tool", null, "why", NOW));
  }

  @Test
  void shouldExposeOnlyTwoDecisions() {
    // given the sealed decision hierarchy
    // when its permitted subclasses are listed
    // then there are exactly two: pending is the absence of a decision, not one of them
    assertEquals(2, ApprovalDecision.class.getPermittedSubclasses().length);
  }

  @Test
  void shouldRejectAnApprovalWithoutAnApprover() {
    // given a blank approver
    // when constructed
    // then it fails: an approval nobody signed is not an approval
    assertThrows(IllegalArgumentException.class, () -> new Approved(" "));
  }

  @Test
  void shouldRejectANullApprover() {
    // given no approver
    // when constructed
    // then it fails
    assertThrows(NullPointerException.class, () -> new Approved(null));
  }

  @Test
  void shouldNameTheDeadlineWhenRejectingByTimeout() {
    // given an expired deadline
    Rejected rejected = Rejected.byTimeout(TWO_MINUTES);

    // when read
    // then the operator can tell an expiry from a refusal, and by whom
    assertEquals(Rejected.SYSTEM, rejected.approver());
    assertEquals("no approval within PT2M", rejected.reason());
  }

  @Test
  void shouldNameTheLimitWhenRejectingByQuota() {
    // given a saturated channel
    Rejected rejected = Rejected.byQuota(20);

    // when read
    // then the reason says which limit was hit, so the operator knows what to raise
    assertEquals(Rejected.SYSTEM, rejected.approver());
    assertTrue(rejected.reason().contains("20"), rejected.reason());
  }

  @Test
  void shouldTellAutomaticRejectionsApartFromHumanOnes() {
    // given an automatic rejection and one from a person
    // when compared
    // then the approver distinguishes them, which is what an audit needs
    assertNotEquals(
        Rejected.byTimeout(TWO_MINUTES).approver(), new Rejected("bob", "no").approver());
  }

  @Test
  void shouldRejectATimeoutRejectionWithoutADuration() {
    // given no duration
    // when built
    // then it fails
    assertThrows(NullPointerException.class, () -> Rejected.byTimeout(null));
  }

  @Test
  void shouldRejectARejectionWithoutAReason() {
    // given a blank reason
    // when constructed
    // then it fails
    assertThrows(IllegalArgumentException.class, () -> new Rejected("bob", " "));
  }

  @Test
  void shouldRejectARejectionWithoutAnApprover() {
    // given a blank approver
    // when constructed
    // then it fails
    assertThrows(IllegalArgumentException.class, () -> new Rejected("", "no"));
  }

  @Test
  void shouldRejectANonPositiveTimeout() {
    // given a timeout of zero
    // when a policy is built
    // then it fails: nothing could ever be approved in time
    assertThrows(
        IllegalArgumentException.class, () -> new ApprovalPolicy(Duration.ZERO, 20, 5, true));
  }

  @Test
  void shouldRejectATimeoutPointingBackwards() {
    // given a negative timeout
    // when a policy is built
    // then it fails
    assertThrows(IllegalArgumentException.class, () -> new ApprovalPolicy(BACKWARDS, 20, 5, true));
  }

  @Test
  void shouldRejectAPolicyWithoutATimeout() {
    // given no timeout
    // when a policy is built
    // then it fails: without one the wait would never end, holding the thread for ever
    assertThrows(NullPointerException.class, () -> new ApprovalPolicy(null, 20, 5, true));
  }

  @Test
  void shouldRejectAGlobalQuotaBelowOne() {
    // given a global quota of zero
    // when a policy is built
    // then it fails: every invocation would be rejected on saturation that cannot be relieved
    assertThrows(IllegalArgumentException.class, () -> new ApprovalPolicy(TWO_MINUTES, 0, 5, true));
  }

  @Test
  void shouldRejectAnAgentQuotaBelowOne() {
    // given a per-agent quota of zero
    // when a policy is built
    // then it fails
    assertThrows(
        IllegalArgumentException.class, () -> new ApprovalPolicy(TWO_MINUTES, 20, 0, true));
  }

  @Test
  void shouldRejectAnAgentQuotaLargerThanTheGlobalOne() {
    // given a per-agent quota above the global one
    // when a policy is built
    // then it fails: it would promise an agent room the channel does not have, making the
    // per-agent limit meaningless
    assertThrows(
        IllegalArgumentException.class, () -> new ApprovalPolicy(TWO_MINUTES, 5, 10, true));
  }

  @Test
  void shouldAcceptAnAgentQuotaEqualToTheGlobalOne() {
    // given both quotas equal
    ApprovalPolicy policy = new ApprovalPolicy(TWO_MINUTES, 5, 5, true);

    // when built
    // then it is accepted: the bound is inclusive
    assertEquals(5, policy.maxPendingPerAgent());
  }

  private static ApprovalRequest request(Map<String, Object> arguments) {
    return new ApprovalRequest(
        ApprovalId.newId(), "agent-1", "delete_table", arguments, "escalated", NOW);
  }
}
