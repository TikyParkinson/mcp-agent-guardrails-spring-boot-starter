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
package io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.in;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretFinding;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretSeverity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultRedactionTest {

  private static final SecretFinding FINDING =
      new SecretFinding("openai-api-key", SecretSeverity.CONFIRMED, "result.text[0]");

  @Test
  void shouldCopySanitizedContentsDefensivelyWhenConstructed() {
    // given
    List<String> mutable = new ArrayList<>(List.of("clean"));
    ResultRedaction redaction = new ResultRedaction(mutable, List.of(), List.of());

    // when
    mutable.add("added later");

    // then
    assertEquals(List.of("clean"), redaction.sanitizedContents());
  }

  @Test
  void shouldCopyTextFindingsDefensivelyWhenConstructed() {
    // given
    List<SecretFinding> mutable = new ArrayList<>();
    ResultRedaction redaction = new ResultRedaction(List.of(), mutable, List.of());

    // when
    mutable.add(FINDING);

    // then
    assertEquals(List.of(), redaction.textFindings());
  }

  @Test
  void shouldCopyStructuredFindingsDefensivelyWhenConstructed() {
    // given
    List<SecretFinding> mutable = new ArrayList<>();
    ResultRedaction redaction = new ResultRedaction(List.of(), List.of(), mutable);

    // when
    mutable.add(FINDING);

    // then
    assertEquals(List.of(), redaction.structuredFindings());
  }

  @Test
  void shouldRejectNullSanitizedContentsWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new ResultRedaction(null, List.of(), List.of()));
  }

  @Test
  void shouldRejectNullTextFindingsWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new ResultRedaction(List.of(), null, List.of()));
  }

  @Test
  void shouldRejectNullStructuredFindingsWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new ResultRedaction(List.of(), List.of(), null));
  }
}
