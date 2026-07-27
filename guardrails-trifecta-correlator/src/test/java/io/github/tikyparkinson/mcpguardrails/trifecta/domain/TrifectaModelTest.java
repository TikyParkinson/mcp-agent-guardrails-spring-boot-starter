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
package io.github.tikyparkinson.mcpguardrails.trifecta.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TrifectaModelTest {

  private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");
  private static final Duration HALF_HOUR = Duration.ofMinutes(30);
  private static final Duration TWO_HOURS = Duration.ofHours(2);
  private static final Set<Capability> ALL_THREE = Set.of(Capability.values());
  private static final Set<Capability> PRIVATE = Set.of(Capability.PRIVATE_DATA);

  @Test
  void shouldDescribeEachCapabilityInReadableForm() {
    // given the three legs
    // when described
    // then the text reads as prose, since it ends up in a reason a person has to act on
    assertEquals("private data", Capability.PRIVATE_DATA.describe());
    assertEquals("untrusted content", Capability.UNTRUSTED_CONTENT.describe());
    assertEquals("external comms", Capability.EXTERNAL_COMMS.describe());
  }

  @Test
  void shouldRejectAToolThatDeclaresNoCapability() {
    // given a tool declared with an empty set
    // when constructed
    // then it fails: declaring a tool that touches nothing is noise, and silently keeping it
    // would suggest the operator covered something they did not
    IllegalArgumentException failure =
        assertThrows(IllegalArgumentException.class, () -> new ToolCapabilities("x", Set.of()));
    assertTrue(failure.getMessage().contains("x"), failure.getMessage());
  }

  @Test
  void shouldRejectAToolWithoutAName() {
    // given a blank tool name
    // when constructed
    // then it fails
    assertThrows(IllegalArgumentException.class, () -> new ToolCapabilities(" ", PRIVATE));
  }

  @Test
  void shouldCopyToolCapabilitiesDefensively() {
    // given a mutable set handed to a declaration
    Set<Capability> declared = new HashSet<>(PRIVATE);
    ToolCapabilities tool = new ToolCapabilities("read_customer", declared);

    // when the caller mutates its own set afterwards
    declared.add(Capability.EXTERNAL_COMMS);

    // then the declaration is unchanged: a tool must not gain capabilities behind the operator's
    // back
    assertEquals(PRIVATE, tool.capabilities());
  }

  @Test
  void shouldKeepMcpSessionsApartFromAgentSessions() {
    // given the same raw value derived both ways
    // when compared
    // then they are different sessions: one identifies a connection, the other a client product
    // shared by everyone using it, and treating them alike would merge unrelated work
    assertNotEquals(SessionId.ofMcpSession("x"), SessionId.ofAgent("x"));
  }

  @Test
  void shouldRejectABlankSessionIdentifier() {
    // given a blank value
    // when constructed
    // then it fails
    assertThrows(IllegalArgumentException.class, () -> new SessionId(" "));
  }

  @Test
  void shouldRejectANullSessionIdentifier() {
    // given no value
    // when constructed
    // then it fails
    assertThrows(NullPointerException.class, () -> new SessionId(null));
  }

  @Test
  void shouldReportNoTrifectaWhenALegIsMissing() {
    // given a session holding two legs
    SessionCapabilities session =
        SessionCapabilities.starting(
            Set.of(Capability.PRIVATE_DATA, Capability.UNTRUSTED_CONTENT), NOW);

    // when checked
    // then it is not complete, and it can say what is left
    assertFalse(session.hasTrifecta());
    assertEquals(Set.of(Capability.EXTERNAL_COMMS), session.missing());
  }

  @Test
  void shouldReportTheTrifectaWhenEveryLegIsPresent() {
    // given a session holding all three
    SessionCapabilities session = SessionCapabilities.starting(ALL_THREE, NOW);

    // when checked
    // then it is complete and nothing is missing
    assertTrue(session.hasTrifecta());
    assertEquals(Set.of(), session.missing());
  }

  @Test
  void shouldKeepTheOriginalStartWhenAddingCapabilities() {
    // given a session started an hour ago
    SessionCapabilities session = SessionCapabilities.starting(PRIVATE, NOW);

    // when a later invocation adds to it
    SessionCapabilities later =
        session.plus(Set.of(Capability.EXTERNAL_COMMS), NOW.plusSeconds(3600));

    // then the start is untouched, which is what the absolute expiry measures against; refreshing
    // it would make a busy session immortal
    assertEquals(NOW, later.startedAt());
    assertEquals(NOW.plusSeconds(3600), later.lastSeenAt());
  }

  @Test
  void shouldNeverLoseACapabilityWhenAdding() {
    // given a session that already saw private data
    SessionCapabilities session = SessionCapabilities.starting(PRIVATE, NOW);

    // when an invocation adds a different leg
    SessionCapabilities later = session.plus(Set.of(Capability.EXTERNAL_COMMS), NOW);

    // then both are held: capabilities are never taken away, which is what keeps a closed trifecta
    // closed for the rest of the session without a separate flag
    assertEquals(Set.of(Capability.PRIVATE_DATA, Capability.EXTERNAL_COMMS), later.capabilities());
  }

  @Test
  void shouldChangeNothingWhenAddingCapabilitiesAlreadyHeld() {
    // given a session holding private data
    SessionCapabilities session = SessionCapabilities.starting(PRIVATE, NOW);

    // when the same leg is added again
    SessionCapabilities later = session.plus(PRIVATE, NOW);

    // then the set is unchanged
    assertEquals(PRIVATE, later.capabilities());
  }

  @Test
  void shouldAcceptASessionWithNoCapabilitiesYet() {
    // given an invocation to a tool nobody declared
    SessionCapabilities session = SessionCapabilities.starting(Set.of(), NOW);

    // when checked
    // then the session exists but holds nothing: an undeclared tool contributes nothing rather
    // than being guessed at
    assertEquals(Set.of(), session.capabilities());
    assertFalse(session.hasTrifecta());
  }

  @Test
  void shouldRejectASessionSeenBeforeItStarted() {
    // given a last-seen instant preceding the start
    // when constructed
    // then it fails: the two clocks would disagree and both expiries would be meaningless
    Instant beforeStart = NOW.minusSeconds(1);
    assertThrows(
        IllegalArgumentException.class, () -> new SessionCapabilities(PRIVATE, NOW, beforeStart));
  }

  @Test
  void shouldRejectASessionWithoutAStart() {
    // given no start instant
    // when constructed
    // then it fails
    assertThrows(NullPointerException.class, () -> new SessionCapabilities(PRIVATE, null, NOW));
  }

  @Test
  void shouldRejectASessionWithoutALastSeen() {
    // given no last-seen instant
    // when constructed
    // then it fails
    assertThrows(NullPointerException.class, () -> new SessionCapabilities(PRIVATE, NOW, null));
  }

  @Test
  void shouldReportThatThisInvocationClosedTheTriangle() {
    // given a session that was incomplete before and is complete now
    SessionAccumulation accumulation =
        new SessionAccumulation(SessionCapabilities.starting(ALL_THREE, NOW), false);

    // when asked
    // then it says so, which is what makes the escalation reason accurate
    assertTrue(accumulation.closedNow());
  }

  @Test
  void shouldReportThatTheTriangleWasAlreadyClosed() {
    // given a session that was already complete
    SessionAccumulation accumulation =
        new SessionAccumulation(SessionCapabilities.starting(ALL_THREE, NOW), true);

    // when asked
    // then this invocation did not close it, though it escalates just the same
    assertFalse(accumulation.closedNow());
  }

  @Test
  void shouldNotReportAClosureWhenTheSessionIsStillIncomplete() {
    // given an incomplete session
    SessionAccumulation accumulation =
        new SessionAccumulation(SessionCapabilities.starting(PRIVATE, NOW), false);

    // when asked
    // then nothing was closed
    assertFalse(accumulation.closedNow());
  }

  @Test
  void shouldRejectAnAccumulationCompleteBeforeButIncompleteAfter() {
    // given a session claimed to have been complete, yet holding two legs afterwards
    SessionCapabilities incomplete = SessionCapabilities.starting(PRIVATE, NOW);

    // when constructed
    // then it fails: capabilities are never removed, so this state cannot exist and would only
    // arise from an adapter reporting the wrong thing
    assertThrows(IllegalArgumentException.class, () -> new SessionAccumulation(incomplete, true));
  }

  @Test
  void shouldRejectAnAccumulationWithoutASession() {
    // given no session
    // when constructed
    // then it fails
    assertThrows(NullPointerException.class, () -> new SessionAccumulation(null, false));
  }

  @Test
  void shouldExposeOnlyTwoVerdicts() {
    // given the sealed verdict hierarchy
    // when its permitted subclasses are listed
    // then there are exactly two: the three legs meet or they do not
    assertEquals(2, TrifectaVerdict.class.getPermittedSubclasses().length);
  }

  @Test
  void shouldRejectAnIncompleteVerdictHoldingEveryLeg() {
    // given all three legs presented as incomplete
    // when constructed
    // then it fails: the two verdicts would overlap and a caller could act on the wrong one
    assertThrows(IllegalArgumentException.class, () -> new TrifectaIncomplete(ALL_THREE));
  }

  @Test
  void shouldRejectACompleteVerdictMissingALeg() {
    // given two legs presented as a complete trifecta
    // when constructed
    // then it fails
    assertThrows(IllegalArgumentException.class, () -> new TrifectaComplete(PRIVATE, true));
  }

  @Test
  void shouldReturnTheDeclaredCapabilitiesOfATool() {
    // given a policy declaring one tool
    TrifectaPolicy policy = policyWith(new ToolCapabilities("read_customer", PRIVATE));

    // when that tool is looked up
    // then its legs come back
    assertEquals(PRIVATE, policy.capabilitiesOf("read_customer"));
  }

  @Test
  void shouldReturnNothingForAToolNobodyDeclared() {
    // given a policy that does not mention a tool
    TrifectaPolicy policy = policyWith(new ToolCapabilities("read_customer", PRIVATE));

    // when an undeclared tool is looked up
    // then it contributes nothing: guessing from a name or description would take input from
    // whoever publishes the MCP server, which is the very thing tool-integrity guards against
    assertEquals(Set.of(), policy.capabilitiesOf("something_else"));
  }

  @Test
  void shouldReportWhenNothingWasDeclared() {
    // given a policy with no tools
    TrifectaPolicy policy = new TrifectaPolicy(List.of(), HALF_HOUR, TWO_HOURS);

    // when asked
    // then it admits it can detect nothing, so the wiring layer can say so out loud
    assertTrue(policy.declaresNothing());
  }

  @Test
  void shouldRejectAPolicyDeclaringTheSameToolTwice() {
    // given two declarations of one tool
    List<ToolCapabilities> tools =
        List.of(
            new ToolCapabilities("read_customer", PRIVATE),
            new ToolCapabilities("read_customer", Set.of(Capability.EXTERNAL_COMMS)));

    // when the policy is built
    // then it fails: one of the two would silently win and the operator would never know which
    assertThrows(
        IllegalArgumentException.class, () -> new TrifectaPolicy(tools, HALF_HOUR, TWO_HOURS));
  }

  @Test
  void shouldRejectAMaximumDurationShorterThanTheIdleTimeout() {
    // given an absolute bound shorter than the idle one
    // when the policy is built
    // then it fails: the idle bound would be unreachable, so configuring it would be a lie
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> new TrifectaPolicy(List.of(), TWO_HOURS, HALF_HOUR));
    assertTrue(failure.getMessage().contains("must not be shorter"), failure.getMessage());
  }

  @Test
  void shouldAcceptAMaximumDurationEqualToTheIdleTimeout() {
    // given both bounds equal
    TrifectaPolicy policy = new TrifectaPolicy(List.of(), HALF_HOUR, HALF_HOUR);

    // when built
    // then it is accepted: the bound is inclusive
    assertEquals(HALF_HOUR, policy.sessionMaxDuration());
  }

  @Test
  void shouldRejectANonPositiveIdleTimeout() {
    // given an idle timeout of zero
    // when the policy is built
    // then it fails: every session would expire before its second invocation
    assertThrows(
        IllegalArgumentException.class,
        () -> new TrifectaPolicy(List.of(), Duration.ZERO, TWO_HOURS));
  }

  @Test
  void shouldRejectANonPositiveMaximumDuration() {
    // given a maximum duration of zero
    // when the policy is built
    // then it fails
    assertThrows(
        IllegalArgumentException.class,
        () -> new TrifectaPolicy(List.of(), HALF_HOUR, Duration.ZERO));
  }

  @Test
  void shouldRejectAnIdleTimeoutPointingBackwards() {
    // given a negative idle timeout, which a typo like PT-30M produces
    // when the policy is built
    // then it fails just as zero does: a duration that runs backwards would expire every session
    // on its first invocation
    Duration backwards = Duration.ofMinutes(-30);
    assertThrows(
        IllegalArgumentException.class, () -> new TrifectaPolicy(List.of(), backwards, TWO_HOURS));
  }

  @Test
  void shouldRejectAPolicyWithoutAnIdleTimeout() {
    // given no idle timeout
    // when the policy is built
    // then it fails
    assertThrows(NullPointerException.class, () -> new TrifectaPolicy(List.of(), null, TWO_HOURS));
  }

  @Test
  void shouldRejectAPolicyWithoutTools() {
    // given no tool list at all
    // when the policy is built
    // then it fails rather than quietly meaning "none declared", which is a different thing
    assertThrows(NullPointerException.class, () -> new TrifectaPolicy(null, HALF_HOUR, TWO_HOURS));
  }

  @Test
  void shouldCopyTheToolListDefensively() {
    // given a mutable list handed to the policy
    List<ToolCapabilities> tools =
        new ArrayList<>(List.of(new ToolCapabilities("read_customer", PRIVATE)));
    TrifectaPolicy policy = new TrifectaPolicy(tools, HALF_HOUR, TWO_HOURS);

    // when the caller clears its own list afterwards
    tools.clear();

    // then the policy still knows the tool
    assertEquals(PRIVATE, policy.capabilitiesOf("read_customer"));
  }

  private static TrifectaPolicy policyWith(ToolCapabilities... tools) {
    return new TrifectaPolicy(List.of(tools), HALF_HOUR, TWO_HOURS);
  }
}
