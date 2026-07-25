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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultVerdictTest {

  private static final ResultEvaluation EVALUATION =
      new ResultEvaluation("credential-leak", new PassThrough());

  @Test
  void shouldCopyEvaluationsDefensivelyWhenConstructed() {
    // given
    List<ResultEvaluation> mutable = new ArrayList<>(List.of(EVALUATION));
    ResultVerdict verdict = new ResultVerdict(new PassThrough(), mutable);

    // when
    mutable.add(new ResultEvaluation("other", new Block("nope")));

    // then
    assertEquals(List.of(EVALUATION), verdict.evaluations());
  }

  @Test
  void shouldRejectNullFinalDecisionWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new ResultVerdict(null, List.of()));
  }

  @Test
  void shouldRejectNullEvaluationsWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new ResultVerdict(new PassThrough(), null));
  }

  @Test
  void shouldRejectNullGuardrailNameWhenEvaluationConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new ResultEvaluation(null, new PassThrough()));
  }

  @Test
  void shouldRejectNullDecisionWhenEvaluationConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new ResultEvaluation("name", null));
  }

  @Test
  void shouldRejectBlankGuardrailNameWhenEvaluationConstructed() {
    // given: an unnamed evaluation makes the trace useless
    PassThrough decision = new PassThrough();

    // when / then
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> new ResultEvaluation(" ", decision));
    assertEquals("guardrailName must not be blank", error.getMessage());
  }
}
