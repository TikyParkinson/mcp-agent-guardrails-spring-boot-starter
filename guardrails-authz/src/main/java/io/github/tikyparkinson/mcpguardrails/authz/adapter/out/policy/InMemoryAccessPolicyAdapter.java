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

import io.github.tikyparkinson.mcpguardrails.authz.application.port.out.AccessPolicyPort;
import io.github.tikyparkinson.mcpguardrails.authz.domain.AccessPolicy;
import java.util.Objects;

/**
 * Default policy source: a fixed, immutable policy (built by the starter from configuration
 * properties). Replace it by exposing your own {@link AccessPolicyPort} bean for dynamic policies.
 */
public final class InMemoryAccessPolicyAdapter implements AccessPolicyPort {

  private final AccessPolicy policy;

  public InMemoryAccessPolicyAdapter(AccessPolicy policy) {
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  @Override
  public AccessPolicy currentPolicy() {
    return policy;
  }
}
