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
package io.github.tikyparkinson.mcpguardrails.injectionguard.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArgumentScannerTest {

  private static final List<InjectionRule> RULES =
      List.of(
          InjectionRule.of("evil", "evil\\s+payload", InjectionSeverity.MALICIOUS),
          InjectionRule.of("sus", "sketchy", InjectionSeverity.SUSPICIOUS));

  @Test
  void shouldReturnCleanWhenArgumentsAreEmpty() {
    // given / when / then
    assertTrue(ArgumentScanner.scan(Map.of(), RULES).clean());
  }

  @Test
  void shouldReturnCleanWhenRuleListIsEmpty() {
    // given / when / then
    assertTrue(ArgumentScanner.scan(Map.of("q", "evil payload"), List.of()).clean());
  }

  @Test
  void shouldFindMatchWithTopLevelPathWhenStringArgumentMatches() {
    // given / when
    ScanResult result = ArgumentScanner.scan(Map.of("query", "an evil payload here"), RULES);

    // then
    assertEquals(
        List.of(new ScanResult.Finding("evil", InjectionSeverity.MALICIOUS, "query")),
        result.findings());
  }

  @Test
  void shouldFindMatchesInNestedMapsAndListsWithFullPaths() {
    // given
    Map<String, Object> arguments =
        Map.of("filters", Map.of("name", "sketchy value"), "items", List.of("ok", "evil payload"));

    // when
    ScanResult result = ArgumentScanner.scan(arguments, RULES);

    // then
    assertEquals(2, result.findings().size());
    assertTrue(
        result
            .findings()
            .contains(new ScanResult.Finding("sus", InjectionSeverity.SUSPICIOUS, "filters.name")));
    assertTrue(
        result
            .findings()
            .contains(new ScanResult.Finding("evil", InjectionSeverity.MALICIOUS, "items[1]")));
  }

  @Test
  void shouldIgnoreNonStringLeavesWhenScanning() {
    // given: numbers, booleans and nulls are not text vectors
    Map<String, Object> arguments = new HashMap<>();
    arguments.put("count", 42);
    arguments.put("flag", true);
    arguments.put("nothing", null);

    // when / then
    assertTrue(ArgumentScanner.scan(arguments, RULES).clean());
  }

  @Test
  void shouldRecordMultipleFindingsWhenSeveralRulesMatchSameValue() {
    // given / when
    ScanResult result = ArgumentScanner.scan(Map.of("q", "sketchy evil payload"), RULES);

    // then
    assertEquals(2, result.findings().size());
  }

  @Test
  void shouldScanValuesNestedPastTheOldDepthLimitWhenNested() {
    // given a payload wrapped in twelve layers, which used to sit below the cap
    Object value = "evil payload";
    for (int i = 0; i < 12; i++) {
      value = Map.of("level", value);
    }

    // when the arguments are scanned
    ScanResult result = ArgumentScanner.scan(Map.of("root", value), RULES);

    // then it is caught. Wrapping a payload in enough layers used to skip the guardrail entirely
    assertFalse(result.clean());
  }

  @Test
  void shouldReportAnIncompleteWalkWhenNestedPastTheBudgetDepth() {
    // given a structure deeper than the budget allows
    Object value = "evil payload";
    for (int i = 0; i < 70; i++) {
      value = Map.of("level", value);
    }

    // when the arguments are scanned
    ScanResult result = ArgumentScanner.scan(Map.of("root", value), RULES);

    // then nothing matched, and the result says the walk did not finish — which is what makes the
    // guardrail deny instead of allow
    assertFalse(result.complete());
  }

  @Test
  void shouldReportAnIncompleteWalkWhenThereAreMoreValuesThanTheBudget() {
    // given a wide structure, which is where scanning actually costs
    Map<String, Object> wide = new HashMap<>();
    for (int field = 0; field < 12_000; field++) {
      wide.put("f" + field, "value " + field);
    }

    // when the arguments are scanned
    ScanResult result = ArgumentScanner.scan(wide, RULES);

    // then the walk stops and says so
    assertFalse(result.complete());
  }

  @Test
  void shouldReportACompleteWalkWhenTheArgumentsFitTheBudget() {
    // given an ordinary call
    // when the arguments are scanned
    ScanResult result = ArgumentScanner.scan(Map.of("q", "sales report"), RULES);

    // then the walk finished, which is the only case that can allow
    assertTrue(result.complete());
  }

  @Test
  void shouldRejectNullInputsWhenScanning() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> ArgumentScanner.scan(null, RULES));
    assertThrows(NullPointerException.class, () -> ArgumentScanner.scan(Map.of(), null));
  }
}
