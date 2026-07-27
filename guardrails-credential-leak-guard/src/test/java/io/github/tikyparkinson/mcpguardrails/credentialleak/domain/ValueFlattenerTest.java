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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValueFlattenerTest {

  private static List<String> paths(Map<String, Object> values) {
    return ValueFlattener.flatten(values).values().stream().map(FlattenedValue::path).toList();
  }

  /**
   * Paths of the values only. Map keys are scanned too since F-5, and these tests are about how the
   * structure is walked rather than about that.
   */
  private static List<String> valuePaths(Map<String, Object> values) {
    return paths(values).stream().filter(path -> !path.contains("{")).toList();
  }

  @Test
  void shouldReturnTopLevelStringsWhenValuesAreFlat() {
    // given / when
    List<FlattenedValue> flattened = ValueFlattener.flatten(Map.of("token", "abc")).values();

    // then: the value, plus the key itself, which is scannable since F-5
    assertTrue(flattened.contains(new FlattenedValue("token", "abc")));
  }

  @Test
  void shouldWalkNestedMapsWhenValuesAreStructured() {
    // given
    Map<String, Object> values = Map.of("db", Map.of("password", "s3cr3t"));

    // when / then
    assertEquals(List.of("db.password"), valuePaths(values));
  }

  @Test
  void shouldIndexListElementsWhenValuesContainLists() {
    // given
    Map<String, Object> values = Map.of("items", List.of("a", "b"));

    // when
    List<String> paths = valuePaths(values);

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
    assertEquals(List.of("token"), valuePaths(values));
  }

  @Test
  void shouldReachValuesPastTheOldDepthLimitWhenNested() {
    // given a value buried twelve levels down, which used to be past the limit
    Map<String, Object> deep = Map.of("leaf", "secret");
    for (int level = 0; level < 12; level++) {
      deep = Map.of("l" + level, deep);
    }

    // when it is flattened
    // then it is reached. Wrapping a payload in enough layers used to hide it from every pattern
    assertEquals(1, valuePaths(deep).size());
  }

  @Test
  void shouldReportAnIncompleteWalkWhenTheStructureIsDeeperThanTheBudget() {
    // given a structure nested past the budget's depth
    Map<String, Object> deep = Map.of("leaf", "secret");
    for (int level = 0; level < 70; level++) {
      deep = Map.of("l" + level, deep);
    }

    // when it is flattened
    // then the result says the walk did not finish. That flag is what makes the guardrail deny:
    // arguments nobody looked at are not arguments known to be clean
    assertFalse(ValueFlattener.flatten(deep).complete());
  }

  @Test
  void shouldReportAnIncompleteWalkWhenThereAreMoreValuesThanTheBudget() {
    // given a wide structure rather than a deep one — which is where the real cost is
    Map<String, Object> wide = new HashMap<>();
    for (int field = 0; field < 6_000; field++) {
      wide.put("f" + field, "value " + field);
    }

    // when it is flattened
    // then the walk stops. Depth was never the expensive shape: a thousand levels hold nine
    // values, while six thousand flat fields hold twelve thousand
    assertFalse(ValueFlattener.flatten(wide).complete());
  }

  @Test
  void shouldReportACompleteWalkWhenTheStructureFitsTheBudget() {
    // given an ordinary call
    Map<String, Object> values = Map.of("token", "abc", "db", Map.of("password", "s3cr3t"));

    // when it is flattened
    // then the walk finished, which is the normal case and the only one that can allow
    assertTrue(ValueFlattener.flatten(values).complete());
  }

  @Test
  void shouldStopEarlyWhenGivenASmallerBudget() {
    // given a budget of three nodes and a structure with more
    Map<String, Object> values = Map.of("a", "1", "b", "2", "c", "3", "d", "4");

    // when it is flattened within that budget
    FlattenedArguments flattened = ValueFlattener.flatten(values, new ScanBudget(3, 64));

    // then it stopped and said so, rather than pretending it saw everything
    assertFalse(flattened.complete());
  }

  @Test
  void shouldReturnImmutableResultWhenFlattening() {
    // given
    List<FlattenedValue> flattened = ValueFlattener.flatten(Map.of("token", "abc")).values();
    FlattenedValue extra = new FlattenedValue("x", "y");

    // when / then
    assertThrows(UnsupportedOperationException.class, () -> flattened.add(extra));
  }

  @Test
  void shouldScanOnlyStringKeysWhenAMapHasOtherKeyTypes() {
    // given a map whose keys are not strings, as a deserialized index can be
    Map<Object, Object> indexed = new HashMap<>();
    indexed.put(1, "first");
    Map<String, Object> values = Map.of("byId", indexed);

    // when it is flattened
    // then the value is still reached and no key is emitted for the number: a number cannot hold
    // a credential, and stringifying it would only add noise
    assertEquals(List.of("byId.1"), valuePaths(values));
    assertTrue(paths(values).stream().noneMatch(path -> path.contains("{1}")));
  }

  @Test
  void shouldRejectNullValuesWhenFlattening() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> ValueFlattener.flatten(null));
  }

  @Test
  void shouldReturnEmptyWhenThereAreNoValues() {
    // given / when / then
    assertEquals(List.of(), ValueFlattener.flatten(Map.of()).values());
  }

  @Test
  void shouldWalkListsInsideMapsWhenBothAreNested() {
    // given
    Map<String, Object> values = Map.of("outer", Map.of("inner", new ArrayList<>(List.of("v"))));

    // when / then
    assertEquals(List.of("outer.inner[0]"), valuePaths(values));
  }
}
