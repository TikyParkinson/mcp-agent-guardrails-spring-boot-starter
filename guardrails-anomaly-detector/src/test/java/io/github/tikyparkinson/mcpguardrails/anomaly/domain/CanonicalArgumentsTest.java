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
package io.github.tikyparkinson.mcpguardrails.anomaly.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CanonicalArgumentsTest {

  @Test
  void shouldRenderTheSameStringWhenKeysArriveInADifferentOrder() {
    // given two maps with the same entries inserted in opposite order
    Map<String, Object> first = new LinkedHashMap<>();
    first.put("alpha", 1);
    first.put("beta", 2);
    Map<String, Object> second = new LinkedHashMap<>();
    second.put("beta", 2);
    second.put("alpha", 1);

    // when rendered
    // then the rendering is identical: map iteration order is not stable across JVMs, and an
    // unstable rendering would stop the repetition heuristic from ever matching
    assertEquals(CanonicalArguments.of(first), CanonicalArguments.of(second));
  }

  @Test
  void shouldRenderDifferentStringsWhenValuesDiffer() {
    // given two maps differing in one value
    // when rendered
    // then the renderings differ
    assertNotEquals(
        CanonicalArguments.of(Map.of("query", "a")), CanonicalArguments.of(Map.of("query", "b")));
  }

  @Test
  void shouldRenderNestedMapsAndListsInOrder() {
    // given nested structures
    Map<String, Object> arguments =
        Map.of("outer", Map.of("inner", "value"), "items", List.of(1, 2));

    // when rendered
    String rendered = CanonicalArguments.of(arguments);

    // then both the nested map and the list appear
    assertEquals("{items:[1;2;];outer:{inner:value;};}", rendered);
  }

  @Test
  void shouldRenderNullValuesExplicitly() {
    // given a map holding a null value
    Map<String, Object> arguments = new LinkedHashMap<>();
    arguments.put("maybe", null);

    // when rendered
    // then the absence is rendered rather than skipped, so it cannot be confused with an empty map
    assertEquals("{maybe:null;}", CanonicalArguments.of(arguments));
  }

  @Test
  void shouldRenderEmptyArgumentsAsAnEmptyMap() {
    // given no arguments
    // when rendered
    // then the result is still a valid canonical form
    assertEquals("{}", CanonicalArguments.of(Map.of()));
  }

  @Test
  void shouldCollapseValuesDeeperThanTheMaximumDepth() {
    // given a value nested past MAX_DEPTH
    Map<String, Object> arguments = nest(CanonicalArguments.MAX_DEPTH + 2, "leaf");

    // when rendered
    String rendered = CanonicalArguments.of(arguments);

    // then it collapses to an ellipsis instead of being rendered in full
    assertTrue(rendered.contains("…"), rendered);
    assertEquals("{root:{k:{k:{k:{k:{k:{k:{k:…;};};};};};};};}", rendered);
  }

  @Test
  void shouldNotOverflowTheStackWhenNestingIsHostile() {
    // given arguments nested one hundred thousand levels deep, as an attacker could send
    Object value = "leaf";
    for (int level = 0; level < 100_000; level++) {
      value = Map.of("k", value);
    }
    Map<String, Object> arguments = Map.of("root", value);

    // when rendered
    // then the depth bound cuts the recursion: a StackOverflowError is an Error, which the chain's
    // safeEvaluate does not catch, so it would take the whole invocation down
    assertEquals("{root:{k:{k:{k:{k:{k:{k:{k:…;};};};};};};};}", CanonicalArguments.of(arguments));
  }

  @Test
  void shouldRejectNullArguments() {
    // given no map at all
    // when rendered
    // then it fails rather than rendering something that looks like empty arguments
    assertThrows(NullPointerException.class, () -> CanonicalArguments.of(null));
  }

  private static Map<String, Object> nest(int depth, Object leaf) {
    Object value = leaf;
    for (int level = 0; level < depth; level++) {
      value = Map.of("k", value);
    }
    return Map.of("root", value);
  }
}
