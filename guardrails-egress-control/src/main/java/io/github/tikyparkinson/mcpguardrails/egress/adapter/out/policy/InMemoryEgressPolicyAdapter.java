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

import io.github.tikyparkinson.mcpguardrails.egress.application.port.out.EgressPolicyPort;
import io.github.tikyparkinson.mcpguardrails.egress.domain.EgressPolicy;
import java.util.Objects;

/**
 * Default policy source: a fixed policy resolved once at startup, normally built from
 * configuration.
 *
 * <p>The policy only changes on restart. Replace this bean to feed the allowlist from a CMDB or a
 * network API; see the module README.
 */
public final class InMemoryEgressPolicyAdapter implements EgressPolicyPort {

  private final EgressPolicy policy;

  public InMemoryEgressPolicyAdapter(EgressPolicy policy) {
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  @Override
  public EgressPolicy currentPolicy() {
    return policy;
  }
}
