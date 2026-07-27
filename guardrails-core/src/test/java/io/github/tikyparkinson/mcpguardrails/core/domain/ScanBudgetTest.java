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

import org.junit.jupiter.api.Test;

class ScanBudgetTest {

  @Test
  void shouldBoundNodesAndDepthWhenBuiltWithDefaults() {
    // given / when
    ScanBudget budget = ScanBudget.defaults();

    // then
    assertEquals(10_000, budget.maxNodes());
    assertEquals(64, budget.maxDepth());
  }

  @Test
  void shouldShareOneDefaultAcrossEveryScannerWhenAskedTwice() {
    // given / when
    ScanBudget first = ScanBudget.defaults();
    ScanBudget second = ScanBudget.defaults();

    // then: two guardrails walking the same arguments truncate at the same point
    assertEquals(first, second);
  }

  @Test
  void shouldKeepTheGivenLimitsWhenBuiltExplicitly() {
    // given / when
    ScanBudget budget = new ScanBudget(25, 3);

    // then
    assertEquals(25, budget.maxNodes());
    assertEquals(3, budget.maxDepth());
  }

  @Test
  void shouldRejectZeroNodesWhenBuilt() {
    // given / when / then: a budget of nothing scans nothing and reports every call unfinished
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> new ScanBudget(0, 64));
    assertEquals("maxNodes must be positive, was 0", thrown.getMessage());
  }

  @Test
  void shouldRejectNegativeNodesWhenBuilt() {
    // given / when / then
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> new ScanBudget(-1, 64));
    assertEquals("maxNodes must be positive, was -1", thrown.getMessage());
  }

  @Test
  void shouldRejectZeroDepthWhenBuilt() {
    // given / when / then
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> new ScanBudget(10, 0));
    assertEquals("maxDepth must be positive, was 0", thrown.getMessage());
  }

  @Test
  void shouldRejectNegativeDepthWhenBuilt() {
    // given / when / then
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> new ScanBudget(10, -5));
    assertEquals("maxDepth must be positive, was -5", thrown.getMessage());
  }
}
