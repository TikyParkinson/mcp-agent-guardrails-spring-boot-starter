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
package io.github.tikyparkinson.mcpguardrails.egress.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.egress.application.port.out.EgressPolicyPort;
import io.github.tikyparkinson.mcpguardrails.egress.domain.AllowedDestination;
import io.github.tikyparkinson.mcpguardrails.egress.domain.Destination;
import io.github.tikyparkinson.mcpguardrails.egress.domain.DestinationsAllowed;
import io.github.tikyparkinson.mcpguardrails.egress.domain.EgressCheckResult;
import io.github.tikyparkinson.mcpguardrails.egress.domain.EgressPolicy;
import io.github.tikyparkinson.mcpguardrails.egress.domain.EgressTool;
import io.github.tikyparkinson.mcpguardrails.egress.domain.EgressViolation;
import io.github.tikyparkinson.mcpguardrails.egress.domain.NotAnEgressTool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CheckEgressDestinationServiceTest {

  private final EgressPolicyPort policyPort = mock(EgressPolicyPort.class);
  private final CheckEgressDestinationService service =
      new CheckEgressDestinationService(policyPort);

  private void policyWith(List<EgressTool> tools, String... allowed) {
    when(policyPort.currentPolicy())
        .thenReturn(
            new EgressPolicy(
                tools, List.of(allowed).stream().map(AllowedDestination::of).toList()));
  }

  private void httpGetAllowing(String... allowed) {
    policyWith(List.of(new EgressTool("http_get", List.of("url"))), allowed);
  }

  private static List<String> hostsOf(EgressCheckResult result) {
    return assertInstanceOf(EgressViolation.class, result).violations().stream()
        .map(Destination::value)
        .toList();
  }

  @Test
  void shouldStayOutOfTheWayWhenToolWasNotDeclaredAsEgressCapable() {
    // given
    httpGetAllowing("api.github.com");

    // when
    EgressCheckResult result = service.check("add", Map.of("a", 1));

    // then
    assertInstanceOf(NotAnEgressTool.class, result);
  }

  @Test
  void shouldAllowWhenDestinationIsOnTheAllowlist() {
    // given
    httpGetAllowing("api.github.com");

    // when
    EgressCheckResult result = service.check("http_get", Map.of("url", "https://api.github.com/x"));

    // then
    assertEquals(
        List.of("api.github.com"),
        assertInstanceOf(DestinationsAllowed.class, result).destinations().stream()
            .map(Destination::value)
            .toList());
  }

  @Test
  void shouldReportViolationWhenDestinationIsNotOnTheAllowlist() {
    // given
    httpGetAllowing("api.github.com");

    // when
    EgressCheckResult result = service.check("http_get", Map.of("url", "https://api.evil.com/x"));

    // then
    assertEquals(List.of("api.evil.com"), hostsOf(result));
  }

  @Test
  void shouldDenyEverythingWhenAllowlistIsEmpty() {
    // given: the out-of-the-box posture — nothing configured, nothing passes
    httpGetAllowing();

    // when
    EgressCheckResult result = service.check("http_get", Map.of("url", "https://api.github.com/x"));

    // then
    assertEquals(List.of("api.github.com"), hostsOf(result));
  }

  @Test
  void shouldReportUnreadableArgumentWhenDestinationCannotBeExtracted() {
    // given: an unreadable destination is what an obfuscated target looks like
    httpGetAllowing("api.github.com");

    // when
    EgressCheckResult result = service.check("http_get", Map.of("url", "not a url"));

    // then
    assertEquals(
        List.of("url"), assertInstanceOf(EgressViolation.class, result).undeterminedArguments());
  }

  @Test
  void shouldReportUnreadableArgumentWhenItIsMissingAltogether() {
    // given
    httpGetAllowing("api.github.com");

    // when
    EgressCheckResult result = service.check("http_get", Map.of());

    // then
    assertEquals(
        List.of("url"), assertInstanceOf(EgressViolation.class, result).undeterminedArguments());
  }

  @Test
  void shouldNotReportHostsWhenSomeArgumentIsUnreadable() {
    // given: one readable and one unreadable destination — the call stops either way
    policyWith(List.of(new EgressTool("send_email", List.of("to", "cc"))), "internal.corp");

    // when
    EgressCheckResult result =
        service.check("send_email", Map.of("to", "user@internal.corp", "cc", "not a host"));

    // then
    EgressViolation violation = assertInstanceOf(EgressViolation.class, result);
    assertEquals(List.of(), violation.violations());
    assertEquals(List.of("cc"), violation.undeterminedArguments());
  }

  @Test
  void shouldCheckEveryDestinationWhenArgumentHoldsAList() {
    // given
    policyWith(List.of(new EgressTool("send_email", List.of("to"))), "internal.corp");

    // when
    EgressCheckResult result =
        service.check("send_email", Map.of("to", List.of("a@internal.corp", "b@evil.com")));

    // then
    assertEquals(List.of("evil.com"), hostsOf(result));
  }

  @Test
  void shouldAllowWhenEveryDestinationOfTheListIsAllowed() {
    // given
    policyWith(List.of(new EgressTool("send_email", List.of("to"))), "internal.corp");

    // when
    EgressCheckResult result =
        service.check("send_email", Map.of("to", List.of("a@internal.corp", "b@internal.corp")));

    // then
    assertInstanceOf(DestinationsAllowed.class, result);
  }

  @Test
  void shouldResolveNestedPathsWhenDeclaredWithDots() {
    // given
    policyWith(List.of(new EgressTool("post", List.of("request.endpoint"))), "api.github.com");

    // when
    EgressCheckResult result =
        service.check("post", Map.of("request", Map.of("endpoint", "https://api.github.com/x")));

    // then
    assertInstanceOf(DestinationsAllowed.class, result);
  }

  @Test
  void shouldCheckEveryDeclaredArgumentWhenThereAreSeveral() {
    // given
    policyWith(List.of(new EgressTool("send_email", List.of("to", "cc"))), "internal.corp");

    // when
    EgressCheckResult result =
        service.check("send_email", Map.of("to", "a@internal.corp", "cc", "leak@evil.com"));

    // then
    assertEquals(List.of("evil.com"), hostsOf(result));
  }

  @Test
  void shouldReadThePolicyOnEveryCallWhenItChanges() {
    // given: the policy is queried per evaluation so it can rotate without a restart
    EgressTool tool = new EgressTool("http_get", List.of("url"));
    when(policyPort.currentPolicy())
        .thenReturn(
            new EgressPolicy(List.of(tool), List.of()),
            new EgressPolicy(List.of(tool), List.of(AllowedDestination.of("api.github.com"))));
    Map<String, Object> arguments = Map.of("url", "https://api.github.com/x");

    // when
    EgressCheckResult first = service.check("http_get", arguments);
    EgressCheckResult second = service.check("http_get", arguments);

    // then
    assertInstanceOf(EgressViolation.class, first);
    assertInstanceOf(DestinationsAllowed.class, second);
  }

  @Test
  void shouldRejectNullToolNameWhenChecking() {
    // given
    Map<String, Object> arguments = Map.of();

    // when / then
    assertThrows(NullPointerException.class, () -> service.check(null, arguments));
  }

  @Test
  void shouldRejectNullArgumentsWhenChecking() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> service.check("http_get", null));
  }

  @Test
  void shouldRejectNullPortWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new CheckEgressDestinationService(null));
  }
}
