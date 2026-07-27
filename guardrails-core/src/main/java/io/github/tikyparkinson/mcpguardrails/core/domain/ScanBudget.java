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

/**
 * How much of a structure a scan is allowed to walk.
 *
 * <p>The cost of scanning is the number of nodes, not how deep they sit. Measured: a structure
 * nested a thousand levels deep flattens to nine values and costs the same as one nested eight,
 * while ten thousand flat fields cost roughly ten milliseconds per scan. Bounding depth alone left
 * the expensive shape unbounded and the cheap one forbidden.
 *
 * <p>{@code maxDepth} is kept as a guard against runaway recursion rather than as a cost control. A
 * recursive walk survived twenty thousand levels in testing, so 64 is generous — but the stack
 * available depends on the JVM, the thread and whether the walk runs on a virtual one, and hostile
 * input should not get to probe that.
 *
 * @param maxNodes values visited before the walk gives up
 * @param maxDepth nesting levels explored before the walk gives up
 */
public record ScanBudget(int maxNodes, int maxDepth) {

  private static final int DEFAULT_MAX_NODES = 10_000;
  private static final int DEFAULT_MAX_DEPTH = 64;

  public ScanBudget {
    if (maxNodes <= 0) {
      throw new IllegalArgumentException("maxNodes must be positive, was " + maxNodes);
    }
    if (maxDepth <= 0) {
      throw new IllegalArgumentException("maxDepth must be positive, was " + maxDepth);
    }
  }

  /**
   * Ten thousand nodes and sixty-four levels. Measured, the worst case the budget allows — five
   * thousand flat fields, two nodes each — costs about 3 ms per scan, and the depth is eight times
   * what it replaced. A legitimate call is nowhere near either: the arguments of a typical tool are
   * scalars, so a handful of nodes at depth one.
   */
  public static ScanBudget defaults() {
    return new ScanBudget(DEFAULT_MAX_NODES, DEFAULT_MAX_DEPTH);
  }
}
