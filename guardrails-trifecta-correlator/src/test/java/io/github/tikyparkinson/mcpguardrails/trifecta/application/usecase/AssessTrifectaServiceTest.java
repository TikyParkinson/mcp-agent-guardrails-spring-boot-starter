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
package io.github.tikyparkinson.mcpguardrails.trifecta.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.trifecta.application.port.out.SessionCapabilityPort;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.Capability;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.SessionAccumulation;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.SessionCapabilities;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.SessionId;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.ToolCapabilities;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.TrifectaComplete;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.TrifectaIncomplete;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.TrifectaPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssessTrifectaServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");
  private static final SessionId SESSION = SessionId.ofMcpSession("s1");
  private static final Set<Capability> PRIVATE = Set.of(Capability.PRIVATE_DATA);
  private static final Set<Capability> ALL_THREE = Set.of(Capability.values());

  private SessionCapabilityPort sessionPort;
  private AssessTrifectaService service;

  @BeforeEach
  void setUp() {
    sessionPort = mock(SessionCapabilityPort.class);
    service = new AssessTrifectaService(sessionPort, policy());
    accumulatesInto(PRIVATE, false);
  }

  @Test
  void shouldReportAnIncompleteTrifectaWhenALegIsMissing() {
    // given a session holding two legs after this invocation
    accumulatesInto(Set.of(Capability.PRIVATE_DATA, Capability.UNTRUSTED_CONTENT), false);

    // when the invocation is assessed
    TrifectaIncomplete verdict =
        assertInstanceOf(TrifectaIncomplete.class, service.assess(SESSION, "read_customer", NOW));

    // then it reports what the session holds, so an operator can see how close it is
    assertEquals(2, verdict.present().size());
  }

  @Test
  void shouldReportACompleteTrifectaWhenTheThreeLegsMeet() {
    // given a session holding all three after this invocation
    accumulatesInto(ALL_THREE, false);

    // when the invocation is assessed
    TrifectaComplete verdict =
        assertInstanceOf(TrifectaComplete.class, service.assess(SESSION, "send_email", NOW));

    // then the three legs travel with the verdict
    assertEquals(ALL_THREE, verdict.capabilities());
  }

  @Test
  void shouldReportThatThisInvocationClosedTheTriangle() {
    // given a session that was incomplete before this invocation
    accumulatesInto(ALL_THREE, false);

    // when the invocation is assessed
    TrifectaComplete verdict =
        assertInstanceOf(TrifectaComplete.class, service.assess(SESSION, "send_email", NOW));

    // then the reason can say so, which is the difference between "this just happened" and "this
    // has been the case for a while"
    assertTrue(verdict.closedNow());
  }

  @Test
  void shouldReportThatTheTriangleWasAlreadyClosed() {
    // given a session that already held the three legs
    accumulatesInto(ALL_THREE, true);

    // when a later invocation is assessed
    TrifectaComplete verdict =
        assertInstanceOf(TrifectaComplete.class, service.assess(SESSION, "get_time", NOW));

    // then it still escalates, but does not claim to have closed anything
    assertFalse(verdict.closedNow());
  }

  @Test
  void shouldKeepReportingACompleteTrifectaForAHarmlessTool() {
    // given a session that already held the three legs
    accumulatesInto(ALL_THREE, true);

    // when a tool that touches nothing is assessed
    // then it escalates too: the session is what is compromised, not the individual call
    assertInstanceOf(TrifectaComplete.class, service.assess(SESSION, "get_time", NOW));
  }

  @Test
  void shouldContributeTheCapabilitiesTheToolDeclares() {
    // given a declared tool
    // when the invocation is assessed
    service.assess(SESSION, "read_customer", NOW);

    // then exactly what the operator declared reaches the session
    ArgumentCaptor<Set<Capability>> contributed = captorForCapabilities();
    verify(sessionPort).accumulate(eq(SESSION), contributed.capture(), eq(NOW));
    assertEquals(PRIVATE, contributed.getValue());
  }

  @Test
  void shouldContributeNothingForAToolNobodyDeclared() {
    // given a tool absent from the policy
    // when the invocation is assessed
    service.assess(SESSION, "unknown_tool", NOW);

    // then it adds nothing rather than being guessed at from its name
    ArgumentCaptor<Set<Capability>> contributed = captorForCapabilities();
    verify(sessionPort).accumulate(eq(SESSION), contributed.capture(), eq(NOW));
    assertEquals(Set.of(), contributed.getValue());
  }

  @Test
  void shouldUseTheInvocationInstantRatherThanAnyOtherClock() {
    // given an invocation carrying its own instant
    // when it is assessed
    service.assess(SESSION, "read_customer", NOW);

    // then that instant is what session expiry will be measured against
    verify(sessionPort).accumulate(eq(SESSION), any(), eq(NOW));
  }

  @Test
  void shouldListTheSessionsTheChannelReportsAsLocked() {
    // given a store holding one closed session
    when(sessionPort.withTrifecta()).thenReturn(List.of(SESSION));

    // when the human side asks
    // then it sees them, since nobody can decide about a list they cannot see
    assertEquals(List.of(SESSION), service.lockedSessions());
  }

  @Test
  void shouldForgetASessionWhenAskedTo() {
    // given a store that holds the session
    when(sessionPort.forget(SESSION)).thenReturn(true);

    // when a person resets it
    // then the store confirms it took effect
    assertTrue(service.reset(SESSION));
  }

  @Test
  void shouldReportWhenThereWasNothingToReset() {
    // given a session the store does not hold
    when(sessionPort.forget(any())).thenReturn(false);

    // when it is reset
    // then the caller learns nothing changed rather than believing it worked
    assertFalse(service.reset(SESSION));
  }

  @Test
  void shouldRejectAnAssessmentWithoutASession() {
    // given no session
    // when assessed
    // then it fails
    assertThrows(NullPointerException.class, () -> service.assess(null, "read_customer", NOW));
  }

  @Test
  void shouldRejectAnAssessmentWithoutAToolName() {
    // given no tool name
    // when assessed
    // then it fails
    assertThrows(NullPointerException.class, () -> service.assess(SESSION, null, NOW));
  }

  @Test
  void shouldRejectAnAssessmentWithoutAnInstant() {
    // given no instant
    // when assessed
    // then it fails
    assertThrows(NullPointerException.class, () -> service.assess(SESSION, "read_customer", null));
  }

  @Test
  void shouldRejectAResetWithoutASession() {
    // given no session
    // when reset
    // then it fails
    assertThrows(NullPointerException.class, () -> service.reset(null));
  }

  @Test
  void shouldRejectAServiceWithoutAStore() {
    // given no store
    TrifectaPolicy policy = policy();

    // when the service is built
    // then it fails at wiring time rather than on the first invocation
    assertThrows(NullPointerException.class, () -> new AssessTrifectaService(null, policy));
  }

  @Test
  void shouldRejectAServiceWithoutAPolicy() {
    // given no policy
    // when the service is built
    // then it fails
    assertThrows(NullPointerException.class, () -> new AssessTrifectaService(sessionPort, null));
  }

  private void accumulatesInto(Set<Capability> held, boolean completeBefore) {
    when(sessionPort.accumulate(any(), any(), any()))
        .thenReturn(
            new SessionAccumulation(SessionCapabilities.starting(held, NOW), completeBefore));
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<Set<Capability>> captorForCapabilities() {
    return ArgumentCaptor.forClass(Set.class);
  }

  private static TrifectaPolicy policy() {
    return new TrifectaPolicy(
        List.of(
            new ToolCapabilities("read_customer", PRIVATE),
            new ToolCapabilities("fetch_url", Set.of(Capability.UNTRUSTED_CONTENT)),
            new ToolCapabilities("send_email", Set.of(Capability.EXTERNAL_COMMS))),
        Duration.ofMinutes(30),
        Duration.ofHours(2));
  }
}
