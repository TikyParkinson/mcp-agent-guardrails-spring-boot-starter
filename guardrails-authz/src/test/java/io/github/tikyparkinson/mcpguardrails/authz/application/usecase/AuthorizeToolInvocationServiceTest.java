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
package io.github.tikyparkinson.mcpguardrails.authz.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.authz.application.port.out.AccessPolicyPort;
import io.github.tikyparkinson.mcpguardrails.authz.domain.AccessPolicy;
import io.github.tikyparkinson.mcpguardrails.authz.domain.PermissionEffect;
import io.github.tikyparkinson.mcpguardrails.authz.domain.PolicyDecision;
import io.github.tikyparkinson.mcpguardrails.authz.domain.PolicyRule;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthorizeToolInvocationServiceTest {

  private final AccessPolicyPort policyPort = mock(AccessPolicyPort.class);
  private final AuthorizeToolInvocationService service =
      new AuthorizeToolInvocationService(policyPort);

  @Test
  void shouldReturnRuleDecisionWhenPolicyMatches() {
    // given
    when(policyPort.currentPolicy())
        .thenReturn(
            new AccessPolicy(
                List.of(new PolicyRule("agent-1", "search", PermissionEffect.DENY)),
                PermissionEffect.ALLOW));

    // when / then
    assertEquals(
        new PolicyDecision(PermissionEffect.DENY, "rule[0]"),
        service.authorize("agent-1", "search"));
  }

  @Test
  void shouldConsultPolicyPortOnEveryCallWhenPolicyChanges() {
    // given: dynamic policy contract — the port is queried per invocation
    when(policyPort.currentPolicy())
        .thenReturn(new AccessPolicy(List.of(), PermissionEffect.ALLOW))
        .thenReturn(new AccessPolicy(List.of(), PermissionEffect.DENY));

    // when / then
    assertEquals(PermissionEffect.ALLOW, service.authorize("a", "t").effect());
    assertEquals(PermissionEffect.DENY, service.authorize("a", "t").effect());
  }

  @Test
  void shouldRejectBlankInputsWhenAuthorizing() {
    // given / when / then
    assertThrows(IllegalArgumentException.class, () -> service.authorize(" ", "tool"));
    assertThrows(IllegalArgumentException.class, () -> service.authorize("agent", ""));
    assertThrows(NullPointerException.class, () -> service.authorize(null, "tool"));
    assertThrows(NullPointerException.class, () -> service.authorize("agent", null));
  }

  @Test
  void shouldRejectNullPortWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new AuthorizeToolInvocationService(null));
  }
}
