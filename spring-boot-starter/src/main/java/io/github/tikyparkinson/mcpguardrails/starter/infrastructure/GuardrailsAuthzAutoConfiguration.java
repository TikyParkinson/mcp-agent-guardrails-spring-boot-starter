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
package io.github.tikyparkinson.mcpguardrails.starter.infrastructure;

import io.github.tikyparkinson.mcpguardrails.authz.adapter.in.chain.AuthzGuardrail;
import io.github.tikyparkinson.mcpguardrails.authz.adapter.out.policy.InMemoryAccessPolicyAdapter;
import io.github.tikyparkinson.mcpguardrails.authz.application.port.in.AuthorizeToolInvocationUseCase;
import io.github.tikyparkinson.mcpguardrails.authz.application.port.out.AccessPolicyPort;
import io.github.tikyparkinson.mcpguardrails.authz.application.usecase.AuthorizeToolInvocationService;
import io.github.tikyparkinson.mcpguardrails.authz.infrastructure.GuardrailsAuthzProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Wires the authz guardrail: property-based policy by default, replaceable via the port. */
@AutoConfiguration(after = GuardrailsAuditAutoConfiguration.class)
@ConditionalOnProperty(name = "mcp.guardrails.enabled", matchIfMissing = true)
@EnableConfigurationProperties(GuardrailsAuthzProperties.class)
public class GuardrailsAuthzAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AccessPolicyPort accessPolicyPort(GuardrailsAuthzProperties properties) {
    return new InMemoryAccessPolicyAdapter(properties.toAccessPolicy());
  }

  @Bean
  @ConditionalOnMissingBean
  public AuthorizeToolInvocationUseCase authorizeToolInvocationUseCase(
      AccessPolicyPort policyPort) {
    return new AuthorizeToolInvocationService(policyPort);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "mcp.guardrails.authz.enabled", matchIfMissing = true)
  public AuthzGuardrail authzGuardrail(AuthorizeToolInvocationUseCase authorize) {
    return new AuthzGuardrail(authorize);
  }
}
