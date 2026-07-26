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
package io.github.tikyparkinson.mcpguardrails.egress.adapter.out.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tikyparkinson.mcpguardrails.egress.domain.AllowedDestination;
import io.github.tikyparkinson.mcpguardrails.egress.domain.EgressPolicy;
import io.github.tikyparkinson.mcpguardrails.egress.domain.EgressTool;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryEgressPolicyAdapterTest {

  @Test
  void shouldReturnTheConfiguredPolicyWhenAsked() {
    // given
    EgressPolicy policy =
        new EgressPolicy(
            List.of(new EgressTool("http_get", List.of("url"))),
            List.of(AllowedDestination.of("api.github.com")));

    // when
    InMemoryEgressPolicyAdapter adapter = new InMemoryEgressPolicyAdapter(policy);

    // then
    assertSame(policy, adapter.currentPolicy());
  }

  @Test
  void shouldReturnTheSamePolicyOnEveryCallWhenBackedByConfiguration() {
    // given: this adapter only changes on restart, which is what the README states
    EgressPolicy policy = new EgressPolicy(List.of(), List.of());
    InMemoryEgressPolicyAdapter adapter = new InMemoryEgressPolicyAdapter(policy);

    // when / then
    assertEquals(adapter.currentPolicy(), adapter.currentPolicy());
  }

  @Test
  void shouldRejectNullPolicyWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new InMemoryEgressPolicyAdapter(null));
  }
}
