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
package io.github.tikyparkinson.mcpguardrails.starter.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.github.tikyparkinson.mcpguardrails.core.application.port.out.EscalationResolver;
import java.util.List;
import org.junit.jupiter.api.Test;

class StarterStartupWarningsTest {

  private static final List<String> ESCALATING = List.of("anomaly-detector", "trifecta-correlator");

  @Test
  void shouldWarnWhenSomethingCanEscalateAndNothingResolvesIt() {
    // given guardrails that can escalate and no resolver in the context
    List<String> warnings = StarterStartupWarnings.of(null, ESCALATING);

    // then one warning is raised: an escalation with nowhere to go returns an error to the agent,
    // which is fail-closed but indistinguishable from a failure, and the operator who enabled
    // those guardrails believes a human is being asked
    assertEquals(1, warnings.size());
  }

  @Test
  void shouldNameTheGuardrailsThatCanEscalate() {
    // given two escalating guardrails and no resolver
    String warning = StarterStartupWarnings.of(null, ESCALATING).getFirst();

    // then the message says which ones, so the operator knows what to act on rather than being
    // told something vague about escalations
    assertTrue(warning.contains("anomaly-detector"), warning);
    assertTrue(warning.contains("trifecta-correlator"), warning);
  }

  @Test
  void shouldPointAtTheTwoWaysOutOfTheProblem() {
    // given the warning
    String warning = StarterStartupWarnings.of(null, ESCALATING).getFirst();

    // then it names both remedies: add the module, or publish your own resolver. A warning that
    // states a problem without a remedy only produces noise
    assertTrue(warning.contains("guardrails-approval-gate"), warning);
    assertTrue(warning.contains("EscalationResolver"), warning);
  }

  @Test
  void shouldStaySilentWhenAResolverIsRegistered() {
    // given a resolver in the context
    EscalationResolver resolver = mock(EscalationResolver.class);

    // when the warnings are collected
    // then there is nothing to say: escalations reach a person
    assertEquals(List.of(), StarterStartupWarnings.of(resolver, ESCALATING));
  }

  @Test
  void shouldStaySilentWhenNothingCanEscalate() {
    // given no guardrail capable of escalating and no resolver either
    // when the warnings are collected
    // then nothing is said: a missing resolver only matters if something would use it
    assertEquals(List.of(), StarterStartupWarnings.of(null, List.of()));
  }

  @Test
  void shouldStaySilentWhenThereIsNeitherResolverNorGuardrails() {
    // given a completely empty wiring
    // when the warnings are collected
    // then nothing is said
    assertTrue(StarterStartupWarnings.of(null, List.of()).isEmpty());
  }

  @Test
  void shouldRejectCollectingWarningsWithoutTheGuardrailNames() {
    // given no list of names
    // when the warnings are collected
    // then it fails rather than treating the absence as "nothing escalates", which would silence
    // the warning precisely when the caller got it wrong
    assertThrows(NullPointerException.class, () -> StarterStartupWarnings.of(null, null));
  }
}
