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

import io.github.tikyparkinson.mcpguardrails.egress.adapter.in.chain.EgressGuardrail;
import io.github.tikyparkinson.mcpguardrails.egress.adapter.out.policy.InMemoryEgressPolicyAdapter;
import io.github.tikyparkinson.mcpguardrails.egress.application.port.in.CheckEgressDestinationUseCase;
import io.github.tikyparkinson.mcpguardrails.egress.application.port.out.EgressPolicyPort;
import io.github.tikyparkinson.mcpguardrails.egress.application.usecase.CheckEgressDestinationService;
import io.github.tikyparkinson.mcpguardrails.egress.infrastructure.GuardrailsEgressProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires the outbound destination allowlist.
 *
 * <p>Nothing is denied until the operator declares which tools reach the network: an undeclared
 * tool is allowed because this guardrail has no opinion about it. Declared ones, on the other hand,
 * face an allowlist that is empty by default.
 */
@AutoConfiguration(after = GuardrailsCoreAutoConfiguration.class)
@ConditionalOnProperty(name = "mcp.guardrails.enabled", matchIfMissing = true)
@EnableConfigurationProperties(GuardrailsEgressProperties.class)
public class GuardrailsEgressAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public EgressPolicyPort egressPolicyPort(GuardrailsEgressProperties properties) {
    return new InMemoryEgressPolicyAdapter(properties.toPolicy());
  }

  @Bean
  @ConditionalOnMissingBean
  public CheckEgressDestinationUseCase checkEgressDestinationUseCase(EgressPolicyPort policyPort) {
    return new CheckEgressDestinationService(policyPort);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "mcp.guardrails.egress.enabled", matchIfMissing = true)
  public EgressGuardrail egressGuardrail(
      CheckEgressDestinationUseCase useCase, GuardrailsEgressProperties properties) {
    return new EgressGuardrail(useCase, properties.onViolation());
  }
}
