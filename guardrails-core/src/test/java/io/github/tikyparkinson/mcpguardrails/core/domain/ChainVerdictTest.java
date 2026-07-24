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

class ChainVerdictTest {

  @Test
  void shouldExposeImmutableEvaluationsWhenSourceListMutates() {
    // given
    List<GuardrailEvaluation> source = new ArrayList<>();
    source.add(new GuardrailEvaluation("a", new Allow()));
    ChainVerdict verdict = new ChainVerdict(new Allow(), source);

    // when
    source.add(new GuardrailEvaluation("b", new Deny("late")));

    // then
    assertEquals(1, verdict.evaluations().size());
  }

  @Test
  void shouldRejectNullsWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new ChainVerdict(null, List.of()));
    assertThrows(NullPointerException.class, () -> new ChainVerdict(new Allow(), null));
    assertThrows(NullPointerException.class, () -> new GuardrailEvaluation("a", null));
    assertThrows(NullPointerException.class, () -> new GuardrailEvaluation(null, new Allow()));
    assertThrows(IllegalArgumentException.class, () -> new GuardrailEvaluation(" ", new Allow()));
  }
}
