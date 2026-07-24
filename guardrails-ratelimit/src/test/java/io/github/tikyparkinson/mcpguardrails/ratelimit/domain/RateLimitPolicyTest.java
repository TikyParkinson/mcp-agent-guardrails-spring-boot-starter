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
package io.github.tikyparkinson.mcpguardrails.ratelimit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RateLimitPolicyTest {

  private static final RateLimitPolicy POLICY = new RateLimitPolicy(3, Duration.ofMinutes(1));

  @Test
  void shouldComputeSameWindowStartWhenInstantsFallInSameWindow() {
    // given
    Instant start = Instant.parse("2026-07-24T10:05:00Z");

    // when / then
    assertEquals(start, POLICY.windowStartFor(Instant.parse("2026-07-24T10:05:00Z")));
    assertEquals(start, POLICY.windowStartFor(Instant.parse("2026-07-24T10:05:59.999Z")));
  }

  @Test
  void shouldComputeNextWindowStartWhenInstantCrossesBoundary() {
    // given / when / then
    assertEquals(
        Instant.parse("2026-07-24T10:06:00Z"),
        POLICY.windowStartFor(Instant.parse("2026-07-24T10:06:00Z")));
  }

  @Test
  void shouldDetectExceededOnlyAboveLimitWhenCounting() {
    // given: limit is 3 — boundary values matter
    assertFalse(POLICY.exceededBy(2));
    assertFalse(POLICY.exceededBy(3));
    assertTrue(POLICY.exceededBy(4));
  }

  @Test
  void shouldDeriveAllowedFromPolicyWhenStatusQueried() {
    // given / when / then
    assertTrue(new RateLimitStatus(3, POLICY).allowed());
    assertFalse(new RateLimitStatus(4, POLICY).allowed());
  }

  @Test
  void shouldRejectInvalidValuesWhenConstructed() {
    // given
    Duration oneMinute = Duration.ofMinutes(1);
    Duration negative = Duration.ofSeconds(-5);

    // when / then
    assertThrows(IllegalArgumentException.class, () -> new RateLimitPolicy(0, oneMinute));
    assertThrows(IllegalArgumentException.class, () -> new RateLimitPolicy(1, Duration.ZERO));
    assertThrows(IllegalArgumentException.class, () -> new RateLimitPolicy(1, negative));
    assertThrows(NullPointerException.class, () -> new RateLimitPolicy(1, null));
    assertThrows(IllegalArgumentException.class, () -> new RateLimitStatus(0, POLICY));
    assertThrows(NullPointerException.class, () -> new RateLimitStatus(1, null));
  }

  @Test
  void shouldRejectNullInstantWhenComputingWindow() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> POLICY.windowStartFor(null));
  }
}
