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
package io.github.tikyparkinson.mcpguardrails.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class DecisionCombinerTest {

  @Test
  void shouldAllowWhenEvaluationsAreEmpty() {
    // given / when
    GuardrailDecision decision = DecisionCombiner.combine(List.of());

    // then
    assertEquals(new Allow(), decision);
  }

  @Test
  void shouldAllowWhenAllEvaluationsAllow() {
    // given
    List<GuardrailEvaluation> evaluations =
        List.of(evaluation("a", new Allow()), evaluation("b", new Allow()));

    // when / then
    assertEquals(new Allow(), DecisionCombiner.combine(evaluations));
  }

  @Test
  void shouldReturnFirstDenyWhenMultipleDeniesPresent() {
    // given
    List<GuardrailEvaluation> evaluations =
        List.of(
            evaluation("a", new Allow()),
            evaluation("b", new Deny("first")),
            evaluation("c", new Deny("second")));

    // when / then
    assertEquals(new Deny("first"), DecisionCombiner.combine(evaluations));
  }

  @Test
  void shouldPreferDenyWhenEscalateComesEarlier() {
    // given
    List<GuardrailEvaluation> evaluations =
        List.of(evaluation("a", new Escalate("check")), evaluation("b", new Deny("blocked")));

    // when / then
    assertEquals(new Deny("blocked"), DecisionCombiner.combine(evaluations));
  }

  @Test
  void shouldReturnFirstEscalateWhenNoDenyPresent() {
    // given
    List<GuardrailEvaluation> evaluations =
        List.of(
            evaluation("a", new Allow()),
            evaluation("b", new Escalate("first")),
            evaluation("c", new Escalate("second")));

    // when / then
    assertEquals(new Escalate("first"), DecisionCombiner.combine(evaluations));
  }

  @Test
  void shouldRejectNullEvaluationsWhenCombining() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> DecisionCombiner.combine(null));
  }

  @Test
  void shouldRejectBlankReasonWhenDenyConstructed() {
    // given / when / then
    assertThrows(IllegalArgumentException.class, () -> new Deny(" "));
    assertThrows(IllegalArgumentException.class, () -> new Escalate(""));
  }

  private static GuardrailEvaluation evaluation(String name, GuardrailDecision decision) {
    return new GuardrailEvaluation(name, decision);
  }
}
