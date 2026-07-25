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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ResultDecisionCombinerTest {

  private static final List<String> ACCUMULATED = List.of("sk-****");

  private static ResultEvaluation evaluation(String name, ResultDecision decision) {
    return new ResultEvaluation(name, decision);
  }

  @Test
  void shouldPassThroughWhenThereAreNoEvaluations() {
    // given / when
    ResultDecision decision = ResultDecisionCombiner.combine(List.of(), List.of());

    // then
    assertInstanceOf(PassThrough.class, decision);
  }

  @Test
  void shouldPassThroughWhenEveryGuardrailPassedThrough() {
    // given
    List<ResultEvaluation> evaluations =
        List.of(evaluation("a", new PassThrough()), evaluation("b", new PassThrough()));

    // when
    ResultDecision decision = ResultDecisionCombiner.combine(evaluations, List.of("plain"));

    // then
    assertInstanceOf(PassThrough.class, decision);
  }

  @Test
  void shouldRedactWithAccumulatedContentsWhenOneGuardrailRedacted() {
    // given: the combined decision must carry what the chain accumulated, not what the
    // individual guardrail returned
    List<ResultEvaluation> evaluations =
        List.of(
            evaluation("a", new PassThrough()),
            evaluation("b", new Redact(List.of("ignored"), "api key")));

    // when
    ResultDecision decision = ResultDecisionCombiner.combine(evaluations, ACCUMULATED);

    // then
    Redact redact = assertInstanceOf(Redact.class, decision);
    assertEquals(ACCUMULATED, redact.sanitizedContents());
    assertEquals("api key", redact.reason());
  }

  @Test
  void shouldJoinEveryReasonWhenSeveralGuardrailsRedacted() {
    // given
    List<ResultEvaluation> evaluations =
        List.of(
            evaluation("a", new Redact(List.of("x"), "api key")),
            evaluation("b", new Redact(List.of("y"), "jwt")));

    // when
    ResultDecision decision = ResultDecisionCombiner.combine(evaluations, ACCUMULATED);

    // then
    assertEquals("api key; jwt", assertInstanceOf(Redact.class, decision).reason());
  }

  @Test
  void shouldBlockWhenAnyGuardrailBlockedEvenIfOthersRedacted() {
    // given: Block outranks Redact
    List<ResultEvaluation> evaluations =
        List.of(
            evaluation("a", new Redact(List.of("x"), "api key")),
            evaluation("b", new Block("secret in structured content")));

    // when
    ResultDecision decision = ResultDecisionCombiner.combine(evaluations, ACCUMULATED);

    // then
    assertEquals("secret in structured content", assertInstanceOf(Block.class, decision).reason());
  }

  @Test
  void shouldKeepFirstBlockWhenSeveralGuardrailsBlocked() {
    // given
    List<ResultEvaluation> evaluations =
        List.of(evaluation("a", new Block("first")), evaluation("b", new Block("second")));

    // when
    ResultDecision decision = ResultDecisionCombiner.combine(evaluations, ACCUMULATED);

    // then
    assertEquals("first", assertInstanceOf(Block.class, decision).reason());
  }

  @Test
  void shouldRejectNullEvaluationsWhenCombining() {
    // given / when / then
    assertThrows(
        NullPointerException.class, () -> ResultDecisionCombiner.combine(null, ACCUMULATED));
  }

  @Test
  void shouldRejectNullAccumulatedContentsWhenCombining() {
    // given
    List<ResultEvaluation> evaluations = List.of();

    // when / then
    assertThrows(
        NullPointerException.class, () -> ResultDecisionCombiner.combine(evaluations, null));
  }
}
