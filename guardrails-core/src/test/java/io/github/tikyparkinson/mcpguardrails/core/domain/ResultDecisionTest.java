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

class ResultDecisionTest {

  @Test
  void shouldCopySanitizedContentsDefensivelyWhenRedactConstructed() {
    // given
    List<String> mutable = new ArrayList<>(List.of("sk-****"));
    Redact redact = new Redact(mutable, "api key redacted");

    // when
    mutable.add("added later");

    // then
    assertEquals(List.of("sk-****"), redact.sanitizedContents());
  }

  @Test
  void shouldRejectNullSanitizedContentsWhenRedactConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new Redact(null, "reason"));
  }

  @Test
  void shouldRejectNullReasonWhenRedactConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new Redact(List.of(), null));
  }

  @Test
  void shouldRejectBlankReasonWhenRedactConstructed() {
    // given: a redaction without a stated reason is untraceable
    List<String> contents = List.of();

    // when / then
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> new Redact(contents, "  "));
    assertEquals("reason must not be blank", error.getMessage());
  }

  @Test
  void shouldRejectNullReasonWhenBlockConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new Block(null));
  }

  @Test
  void shouldRejectBlankReasonWhenBlockConstructed() {
    // given / when / then
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> new Block(""));
    assertEquals("reason must not be blank", error.getMessage());
  }

  @Test
  void shouldExposeReasonWhenBlockConstructed() {
    // given / when
    Block block = new Block("credential found in structured content");

    // then
    assertEquals("credential found in structured content", block.reason());
  }

  @Test
  void shouldBeEqualByValueWhenPassThroughConstructed() {
    // given / when / then
    assertEquals(new PassThrough(), new PassThrough());
  }
}
