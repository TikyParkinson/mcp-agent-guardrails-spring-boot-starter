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
package io.github.tikyparkinson.mcpguardrails.authz.adapter.out.policy;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tikyparkinson.mcpguardrails.authz.domain.AccessPolicy;
import io.github.tikyparkinson.mcpguardrails.authz.domain.PermissionEffect;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryAccessPolicyAdapterTest {

  @Test
  void shouldReturnSamePolicyWhenQueried() {
    // given
    AccessPolicy policy = new AccessPolicy(List.of(), PermissionEffect.DENY);
    InMemoryAccessPolicyAdapter adapter = new InMemoryAccessPolicyAdapter(policy);

    // when / then
    assertSame(policy, adapter.currentPolicy());
  }

  @Test
  void shouldRejectNullPolicyWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new InMemoryAccessPolicyAdapter(null));
  }
}
