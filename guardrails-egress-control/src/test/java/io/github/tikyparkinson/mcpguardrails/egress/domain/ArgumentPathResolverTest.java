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
package io.github.tikyparkinson.mcpguardrails.egress.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArgumentPathResolverTest {

  @Test
  void shouldReturnTheValueWhenPathIsATopLevelString() {
    // given / when / then
    assertEquals(
        List.of("https://x.com"),
        ArgumentPathResolver.resolve(Map.of("url", "https://x.com"), "url"));
  }

  @Test
  void shouldWalkNestedMapsWhenPathIsDotted() {
    // given
    Map<String, Object> arguments = Map.of("request", Map.of("endpoint", "https://x.com"));

    // when / then
    assertEquals(
        List.of("https://x.com"), ArgumentPathResolver.resolve(arguments, "request.endpoint"));
  }

  @Test
  void shouldReturnEveryElementWhenPathLeadsToAList() {
    // given
    Map<String, Object> arguments = Map.of("to", List.of("a@x.com", "b@y.com"));

    // when / then
    assertEquals(List.of("a@x.com", "b@y.com"), ArgumentPathResolver.resolve(arguments, "to"));
  }

  @Test
  void shouldKeepOnlyStringsWhenListIsMixed() {
    // given
    List<Object> mixed = new ArrayList<>(List.of("a@x.com", 42, true));
    Map<String, Object> arguments = Map.of("to", mixed);

    // when / then
    assertEquals(List.of("a@x.com"), ArgumentPathResolver.resolve(arguments, "to"));
  }

  @Test
  void shouldReturnNothingWhenPathIsMissing() {
    // given / when / then
    assertEquals(List.of(), ArgumentPathResolver.resolve(Map.of("other", "x"), "url"));
  }

  @Test
  void shouldReturnNothingWhenPathLeadsIntoANonMap() {
    // given: "url" is a string, so "url.deeper" cannot be resolved
    Map<String, Object> arguments = Map.of("url", "https://x.com");

    // when / then
    assertEquals(List.of(), ArgumentPathResolver.resolve(arguments, "url.deeper"));
  }

  @Test
  void shouldReturnNothingWhenValueIsNotAString() {
    // given / when / then
    assertEquals(List.of(), ArgumentPathResolver.resolve(Map.of("url", 42), "url"));
  }

  @Test
  void shouldReturnNothingWhenValueIsBlank() {
    // given: a blank destination is as unusable as a missing one
    // when / then
    assertEquals(List.of(), ArgumentPathResolver.resolve(Map.of("url", "   "), "url"));
  }

  @Test
  void shouldReturnNothingWhenValueIsNull() {
    // given
    Map<String, Object> arguments = new HashMap<>();
    arguments.put("url", null);

    // when / then
    assertEquals(List.of(), ArgumentPathResolver.resolve(arguments, "url"));
  }

  @Test
  void shouldRejectNullArgumentsWhenResolving() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> ArgumentPathResolver.resolve(null, "url"));
  }

  @Test
  void shouldRejectNullPathWhenResolving() {
    // given
    Map<String, Object> arguments = Map.of();

    // when / then
    assertThrows(NullPointerException.class, () -> ArgumentPathResolver.resolve(arguments, null));
  }
}
