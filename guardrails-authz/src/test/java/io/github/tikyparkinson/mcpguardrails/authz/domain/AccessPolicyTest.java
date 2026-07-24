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
package io.github.tikyparkinson.mcpguardrails.authz.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccessPolicyTest {

  @Test
  void shouldMatchWhenPatternsAreExactOrWildcard() {
    // given
    PolicyRule exact = new PolicyRule("agent-1", "search", PermissionEffect.ALLOW);
    PolicyRule wildcard = new PolicyRule("*", "*", PermissionEffect.DENY);

    // when / then
    assertTrue(exact.matches("agent-1", "search"));
    assertFalse(exact.matches("agent-2", "search"));
    assertFalse(exact.matches("agent-1", "delete"));
    assertTrue(wildcard.matches("anything", "whatever"));
  }

  @Test
  void shouldNotMatchWhenCaseDiffers() {
    // given: matching is case-sensitive by spec
    PolicyRule rule = new PolicyRule("Agent-1", "Search", PermissionEffect.ALLOW);

    // when / then
    assertFalse(rule.matches("agent-1", "search"));
  }

  @Test
  void shouldUseFirstMatchingRuleWhenSeveralMatch() {
    // given: first-match-wins semantics
    AccessPolicy policy =
        new AccessPolicy(
            List.of(
                new PolicyRule("*", "delete_db", PermissionEffect.ESCALATE),
                new PolicyRule("agent-1", "*", PermissionEffect.ALLOW),
                new PolicyRule("*", "*", PermissionEffect.DENY)),
            PermissionEffect.ALLOW);

    // when / then
    assertEquals(
        new PolicyDecision(PermissionEffect.ESCALATE, "rule[0]"),
        policy.decide("agent-1", "delete_db"));
    assertEquals(
        new PolicyDecision(PermissionEffect.ALLOW, "rule[1]"), policy.decide("agent-1", "search"));
    assertEquals(
        new PolicyDecision(PermissionEffect.DENY, "rule[2]"), policy.decide("agent-2", "search"));
  }

  @Test
  void shouldFallBackToDefaultWhenNoRuleMatches() {
    // given
    AccessPolicy policy =
        new AccessPolicy(
            List.of(new PolicyRule("agent-1", "search", PermissionEffect.DENY)),
            PermissionEffect.ESCALATE);

    // when / then
    assertEquals(
        new PolicyDecision(PermissionEffect.ESCALATE, "default"),
        policy.decide("agent-2", "other"));
  }

  @Test
  void shouldFallBackToDefaultWhenRuleListIsEmpty() {
    // given
    AccessPolicy policy = new AccessPolicy(List.of(), PermissionEffect.ALLOW);

    // when / then
    assertEquals(
        new PolicyDecision(PermissionEffect.ALLOW, "default"), policy.decide("any", "tool"));
  }

  @Test
  void shouldExposeImmutableRulesWhenSourceListMutates() {
    // given
    List<PolicyRule> source = new ArrayList<>();
    source.add(new PolicyRule("*", "*", PermissionEffect.DENY));
    AccessPolicy policy = new AccessPolicy(source, PermissionEffect.ALLOW);

    // when
    source.clear();

    // then
    assertEquals(1, policy.rules().size());
  }

  @Test
  void shouldRejectInvalidFieldsWhenConstructed() {
    // given / when / then
    assertThrows(
        IllegalArgumentException.class, () -> new PolicyRule(" ", "*", PermissionEffect.ALLOW));
    assertThrows(
        IllegalArgumentException.class, () -> new PolicyRule("*", "", PermissionEffect.ALLOW));
    assertThrows(NullPointerException.class, () -> new PolicyRule("*", "*", null));
    assertThrows(NullPointerException.class, () -> new AccessPolicy(null, PermissionEffect.ALLOW));
    assertThrows(NullPointerException.class, () -> new AccessPolicy(List.of(), null));
    assertThrows(
        IllegalArgumentException.class, () -> new PolicyDecision(PermissionEffect.ALLOW, " "));
  }

  @Test
  void shouldRejectNullInputsWhenDeciding() {
    // given
    AccessPolicy policy = new AccessPolicy(List.of(), PermissionEffect.ALLOW);

    // when / then
    assertThrows(NullPointerException.class, () -> policy.decide(null, "tool"));
    assertThrows(NullPointerException.class, () -> policy.decide("agent", null));
  }
}
