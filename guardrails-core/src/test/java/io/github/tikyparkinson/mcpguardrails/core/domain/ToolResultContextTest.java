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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolResultContextTest {

  private static final AgentId AGENT = new AgentId("copilot");
  private static final ToolName TOOL = new ToolName("search");
  private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");

  private static ToolResultContext context(List<String> texts, Map<String, Object> structured) {
    return new ToolResultContext(AGENT, TOOL, NOW, texts, structured, false);
  }

  @Test
  void shouldCopyTextContentsDefensivelyWhenConstructed() {
    // given: a caller keeping a reference to the list it passed in
    List<String> mutable = new ArrayList<>(List.of("secret"));
    ToolResultContext context = context(mutable, Map.of());

    // when
    mutable.add("added later");

    // then
    assertEquals(List.of("secret"), context.textContents());
  }

  @Test
  void shouldCopyStructuredContentDefensivelyWhenConstructed() {
    // given
    Map<String, Object> mutable = new HashMap<>(Map.of("token", "abc"));
    ToolResultContext context = context(List.of(), mutable);

    // when
    mutable.put("added", "later");

    // then
    assertEquals(Map.of("token", "abc"), context.structuredContent());
  }

  @Test
  void shouldRejectNullAgentIdWhenConstructed() {
    // given / when / then
    assertThrows(
        NullPointerException.class,
        () -> new ToolResultContext(null, TOOL, NOW, List.of(), Map.of(), false));
  }

  @Test
  void shouldRejectNullToolNameWhenConstructed() {
    // given / when / then
    assertThrows(
        NullPointerException.class,
        () -> new ToolResultContext(AGENT, null, NOW, List.of(), Map.of(), false));
  }

  @Test
  void shouldRejectNullOccurredAtWhenConstructed() {
    // given / when / then
    assertThrows(
        NullPointerException.class,
        () -> new ToolResultContext(AGENT, TOOL, null, List.of(), Map.of(), false));
  }

  @Test
  void shouldRejectNullTextContentsWhenConstructed() {
    // given / when / then
    assertThrows(
        NullPointerException.class,
        () -> new ToolResultContext(AGENT, TOOL, NOW, null, Map.of(), false));
  }

  @Test
  void shouldRejectNullStructuredContentWhenConstructed() {
    // given / when / then
    assertThrows(
        NullPointerException.class,
        () -> new ToolResultContext(AGENT, TOOL, NOW, List.of(), null, false));
  }

  @Test
  void shouldPreserveErrorFlagWhenConstructed() {
    // given / when
    ToolResultContext failed = new ToolResultContext(AGENT, TOOL, NOW, List.of(), Map.of(), true);

    // then
    assertTrue(failed.error());
    assertFalse(context(List.of(), Map.of()).error());
  }

  @Test
  void shouldReplaceTextsKeepingEverythingElseWhenSizeMatches() {
    // given
    ToolResultContext original =
        new ToolResultContext(
            AGENT, TOOL, NOW, List.of("sk-live-1", "plain"), Map.of("k", "v"), true);

    // when
    ToolResultContext redacted = original.withTextContents(List.of("sk-****", "plain"));

    // then
    assertEquals(List.of("sk-****", "plain"), redacted.textContents());
    assertEquals(original.agentId(), redacted.agentId());
    assertEquals(original.toolName(), redacted.toolName());
    assertEquals(original.occurredAt(), redacted.occurredAt());
    assertEquals(original.structuredContent(), redacted.structuredContent());
    assertTrue(redacted.error());
  }

  @Test
  void shouldFailWhenReplacementSizeDiffers() {
    // given: positional replacement cannot be honoured with a different size
    ToolResultContext original = context(List.of("a", "b"), Map.of());
    List<String> tooFew = List.of("a");

    // when / then
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> original.withTextContents(tooFew));
    assertEquals("replacements must have size 2, was 1", error.getMessage());
  }

  @Test
  void shouldRejectNullReplacementsWhenReplacingTexts() {
    // given
    ToolResultContext original = context(List.of("a"), Map.of());

    // when / then
    assertThrows(NullPointerException.class, () -> original.withTextContents(null));
  }
}
