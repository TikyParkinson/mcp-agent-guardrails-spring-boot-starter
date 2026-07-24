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
package io.github.tikyparkinson.mcpguardrails.authz.application.usecase;

import io.github.tikyparkinson.mcpguardrails.authz.application.port.in.AuthorizeToolInvocationUseCase;
import io.github.tikyparkinson.mcpguardrails.authz.application.port.out.AccessPolicyPort;
import io.github.tikyparkinson.mcpguardrails.authz.domain.PolicyDecision;
import java.util.Objects;

/** Fetches the current policy and lets the domain decide. */
public final class AuthorizeToolInvocationService implements AuthorizeToolInvocationUseCase {

  private final AccessPolicyPort policyPort;

  public AuthorizeToolInvocationService(AccessPolicyPort policyPort) {
    this.policyPort = Objects.requireNonNull(policyPort, "policyPort");
  }

  @Override
  public PolicyDecision authorize(String agentId, String toolName) {
    requireNotBlank(agentId, "agentId");
    requireNotBlank(toolName, "toolName");
    return policyPort.currentPolicy().decide(agentId, toolName);
  }

  private static void requireNotBlank(String value, String field) {
    Objects.requireNonNull(value, field);
    if (value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
