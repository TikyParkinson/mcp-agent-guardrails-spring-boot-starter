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
package io.github.tikyparkinson.mcpguardrails.credentialleak.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValueFlattenerTest {

  private static List<String> paths(Map<String, Object> values) {
    return ValueFlattener.flatten(values).stream().map(FlattenedValue::path).toList();
  }

  @Test
  void shouldReturnTopLevelStringsWhenValuesAreFlat() {
    // given / when
    List<FlattenedValue> flattened = ValueFlattener.flatten(Map.of("token", "abc"));

    // then
    assertEquals(List.of(new FlattenedValue("token", "abc")), flattened);
  }

  @Test
  void shouldWalkNestedMapsWhenValuesAreStructured() {
    // given
    Map<String, Object> values = Map.of("db", Map.of("password", "s3cr3t"));

    // when / then
    assertEquals(List.of("db.password"), paths(values));
  }

  @Test
  void shouldIndexListElementsWhenValuesContainLists() {
    // given
    Map<String, Object> values = Map.of("items", List.of("a", "b"));

    // when
    List<String> paths = paths(values);

    // then
    assertEquals(List.of("items[0]", "items[1]"), paths);
  }

  @Test
  void shouldSkipNonStringLeavesWhenFlattening() {
    // given: numbers, booleans and nulls cannot hold a credential
    Map<String, Object> values = new HashMap<>();
    values.put("count", 42);
    values.put("enabled", true);
    values.put("missing", null);
    values.put("token", "abc");

    // when / then
    assertEquals(List.of("token"), paths(values));
  }

  @Test
  void shouldStopAtMaxDepthWhenStructureIsDeeplyNested() {
    // given: bounded recursion protects against nesting bombs
    Map<String, Object> deep = Map.of("leaf", "secret");
    for (int level = 0; level < 12; level++) {
      deep = Map.of("l" + level, deep);
    }

    // when / then
    assertTrue(ValueFlattener.flatten(deep).isEmpty());
  }

  @Test
  void shouldReachValuesJustWithinMaxDepthWhenNested() {
    // given: depth 8 is still explored — the boundary itself must work
    Map<String, Object> nested = Map.of("leaf", "secret");
    for (int level = 0; level < ValueFlattener.MAX_DEPTH - 2; level++) {
      nested = Map.of("l" + level, nested);
    }

    // when / then
    assertEquals(1, ValueFlattener.flatten(nested).size());
  }

  @Test
  void shouldReturnImmutableResultWhenFlattening() {
    // given
    List<FlattenedValue> flattened = ValueFlattener.flatten(Map.of("token", "abc"));
    FlattenedValue extra = new FlattenedValue("x", "y");

    // when / then
    assertThrows(UnsupportedOperationException.class, () -> flattened.add(extra));
  }

  @Test
  void shouldRejectNullValuesWhenFlattening() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> ValueFlattener.flatten(null));
  }

  @Test
  void shouldReturnEmptyWhenThereAreNoValues() {
    // given / when / then
    assertEquals(List.of(), ValueFlattener.flatten(Map.of()));
  }

  @Test
  void shouldWalkListsInsideMapsWhenBothAreNested() {
    // given
    Map<String, Object> values = Map.of("outer", Map.of("inner", new ArrayList<>(List.of("v"))));

    // when / then
    assertEquals(List.of("outer.inner[0]"), paths(values));
  }
}
