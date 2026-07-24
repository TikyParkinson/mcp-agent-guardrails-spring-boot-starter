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
package io.github.tikyparkinson.mcpguardrails.ratelimit.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.ratelimit.application.port.out.RateLimitStorePort;
import io.github.tikyparkinson.mcpguardrails.ratelimit.domain.RateLimitPolicy;
import io.github.tikyparkinson.mcpguardrails.ratelimit.domain.RateLimitStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CheckRateLimitServiceTest {

  private static final RateLimitPolicy POLICY = new RateLimitPolicy(2, Duration.ofMinutes(1));
  private static final Instant NOW = Instant.parse("2026-07-24T10:05:30Z");
  private static final Instant WINDOW_START = Instant.parse("2026-07-24T10:05:00Z");

  private final RateLimitStorePort store = mock(RateLimitStorePort.class);
  private final CheckRateLimitService service = new CheckRateLimitService(store, POLICY);

  @Test
  void shouldAllowWhenCountWithinLimit() {
    // given: the service passes the domain-computed window start to the store
    when(store.incrementAndCount("agent-1", "search", WINDOW_START)).thenReturn(2L);

    // when
    RateLimitStatus status = service.check("agent-1", "search", NOW);

    // then
    assertTrue(status.allowed());
    assertEquals(2, status.count());
  }

  @Test
  void shouldReportExceededWhenCountAboveLimit() {
    // given
    when(store.incrementAndCount("agent-1", "search", WINDOW_START)).thenReturn(3L);

    // when / then
    assertFalse(service.check("agent-1", "search", NOW).allowed());
  }

  @Test
  void shouldPropagateStoreFailureWhenIncrementThrows() {
    // given: fail-closed contract
    when(store.incrementAndCount(anyString(), anyString(), any()))
        .thenThrow(new IllegalStateException("store down"));

    // when / then
    assertThrows(IllegalStateException.class, () -> service.check("agent-1", "search", NOW));
  }

  @Test
  void shouldRejectInvalidInputsWhenChecking() {
    // given / when / then
    assertThrows(IllegalArgumentException.class, () -> service.check(" ", "tool", NOW));
    assertThrows(IllegalArgumentException.class, () -> service.check("agent", "", NOW));
    assertThrows(NullPointerException.class, () -> service.check("agent", "tool", null));
  }

  @Test
  void shouldRejectNullCollaboratorsWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new CheckRateLimitService(null, POLICY));
    assertThrows(NullPointerException.class, () -> new CheckRateLimitService(store, null));
  }
}
