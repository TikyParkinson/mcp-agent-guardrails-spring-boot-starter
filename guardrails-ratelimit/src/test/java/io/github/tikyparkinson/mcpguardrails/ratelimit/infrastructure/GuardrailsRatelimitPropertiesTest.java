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
package io.github.tikyparkinson.mcpguardrails.ratelimit.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.ratelimit.domain.RateLimitPolicy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class GuardrailsRatelimitPropertiesTest {

  @Test
  void shouldEnableWithSixtyPerMinuteWhenDefaultConstructorUsed() {
    // given / when
    GuardrailsRatelimitProperties properties = new GuardrailsRatelimitProperties();

    // then
    assertTrue(properties.enabled());
    assertEquals(60, properties.maxInvocations());
    assertEquals(Duration.ofMinutes(1), properties.window());
  }

  @Test
  void shouldNormalizeNullWindowWhenBoundWithMissingValue() {
    // given / when
    GuardrailsRatelimitProperties properties = new GuardrailsRatelimitProperties(true, 10, null);

    // then
    assertEquals(Duration.ofMinutes(1), properties.window());
  }

  @Test
  void shouldBuildDomainPolicyWhenConfigured() {
    // given
    GuardrailsRatelimitProperties properties =
        new GuardrailsRatelimitProperties(true, 5, Duration.ofSeconds(30));

    // when / then
    assertEquals(new RateLimitPolicy(5, Duration.ofSeconds(30)), properties.toPolicy());
  }
}
