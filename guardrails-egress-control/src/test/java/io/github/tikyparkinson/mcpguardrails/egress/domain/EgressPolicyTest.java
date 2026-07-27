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
package io.github.tikyparkinson.mcpguardrails.egress.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EgressPolicyTest {

  private static final EgressTool HTTP_GET = new EgressTool("http_get", List.of("url"));

  private static EgressPolicy policyAllowing(String... patterns) {
    return new EgressPolicy(
        List.of(HTTP_GET), List.of(patterns).stream().map(AllowedDestination::of).toList());
  }

  @Test
  void shouldFindTheDeclarationWhenToolWasDeclared() {
    // given / when / then
    assertEquals(Optional.of(HTTP_GET), policyAllowing().egressToolNamed("http_get"));
  }

  @Test
  void shouldFindNothingWhenToolWasNotDeclared() {
    // given / when / then
    assertEquals(Optional.empty(), policyAllowing().egressToolNamed("add"));
  }

  @Test
  void shouldDenyEveryDestinationWhenAllowlistIsEmpty() {
    // given: the default posture of the module
    EgressPolicy policy = policyAllowing();

    // when / then
    assertFalse(policy.allows(Destination.of("api.github.com")));
  }

  @Test
  void shouldAllowWhenAnyEntryMatches() {
    // given
    EgressPolicy policy = policyAllowing("api.github.com", "*.internal.corp");

    // when / then
    assertTrue(policy.allows(Destination.of("a.internal.corp")));
  }

  @Test
  void shouldDenyWhenNoEntryMatches() {
    // given
    EgressPolicy policy = policyAllowing("api.github.com", "*.internal.corp");

    // when / then
    assertFalse(policy.allows(Destination.of("api.evil.com")));
  }

  @Test
  void shouldCopyToolsDefensivelyWhenConstructed() {
    // given
    List<EgressTool> mutable = new ArrayList<>(List.of(HTTP_GET));
    EgressPolicy policy = new EgressPolicy(mutable, List.of());

    // when
    mutable.add(new EgressTool("other", List.of("url")));

    // then
    assertEquals(1, policy.tools().size());
  }

  @Test
  void shouldCopyAllowedDestinationsDefensivelyWhenConstructed() {
    // given
    List<AllowedDestination> mutable = new ArrayList<>();
    EgressPolicy policy = new EgressPolicy(List.of(), mutable);

    // when
    mutable.add(AllowedDestination.of("api.github.com"));

    // then
    assertEquals(List.of(), policy.allowedDestinations());
  }

  @Test
  void shouldRejectNullToolsWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new EgressPolicy(null, List.of()));
  }

  @Test
  void shouldRejectNullAllowedDestinationsWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new EgressPolicy(List.of(), null));
  }

  @Test
  void shouldRejectNullToolNameWhenLookingUp() {
    // given
    EgressPolicy policy = policyAllowing();

    // when / then
    assertThrows(NullPointerException.class, () -> policy.egressToolNamed(null));
  }

  @Test
  void shouldRejectNullDestinationWhenCheckingTheAllowlist() {
    // given
    EgressPolicy policy = policyAllowing();

    // when / then
    assertThrows(NullPointerException.class, () -> policy.allows(null));
  }

  @Test
  void shouldFailWhenEgressToolDeclaresNoDestinationArgument() {
    // given: registering an egress tool with nothing to check is a silent fail-open
    List<String> none = List.of();

    // when / then
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> new EgressTool("http_get", none));
    assertEquals(
        "egress tool http_get must declare at least one destination argument", error.getMessage());
  }

  @Test
  void shouldFailWhenEgressToolNameIsBlank() {
    // given
    List<String> paths = List.of("url");

    // when / then
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> new EgressTool(" ", paths));
    assertEquals("toolName must not be blank", error.getMessage());
  }

  @Test
  void shouldFailWhenADestinationArgumentIsBlank() {
    // given
    List<String> paths = List.of("url", " ");

    // when / then
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> new EgressTool("http_get", paths));
    assertEquals("destination arguments of http_get must not be blank", error.getMessage());
  }

  @Test
  void shouldRejectNullToolNameWhenEgressToolConstructed() {
    // given
    List<String> paths = List.of("url");

    // when / then
    assertThrows(NullPointerException.class, () -> new EgressTool(null, paths));
  }

  @Test
  void shouldRejectNullDestinationArgumentsWhenEgressToolConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new EgressTool("http_get", null));
  }
}
