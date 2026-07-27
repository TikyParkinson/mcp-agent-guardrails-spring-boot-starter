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

import io.github.tikyparkinson.mcpguardrails.trifecta.adapter.in.chain.SessionIdResolver;
import io.github.tikyparkinson.mcpguardrails.trifecta.adapter.in.chain.TrifectaGuardrail;
import io.github.tikyparkinson.mcpguardrails.trifecta.adapter.out.session.InMemorySessionCapabilityAdapter;
import io.github.tikyparkinson.mcpguardrails.trifecta.application.port.in.AssessTrifectaUseCase;
import io.github.tikyparkinson.mcpguardrails.trifecta.application.port.in.ResetSessionUseCase;
import io.github.tikyparkinson.mcpguardrails.trifecta.application.port.out.SessionCapabilityPort;
import io.github.tikyparkinson.mcpguardrails.trifecta.application.usecase.AssessTrifectaService;
import io.github.tikyparkinson.mcpguardrails.trifecta.infrastructure.GuardrailsTrifectaProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires the lethal trifecta correlator.
 *
 * <p>{@link AssessTrifectaService} implements both the assessing and the resetting side, so a
 * single instance is published under both types: two would hold separate session maps, and a person
 * resetting one would not unblock the other.
 *
 * <p>Detects nothing until the operator declares which tools touch which legs. The module reports
 * that at start-up rather than looking healthy — see {@code TrifectaStartupWarnings}.
 */
@AutoConfiguration(after = GuardrailsCoreAutoConfiguration.class)
@ConditionalOnProperty(name = "mcp.guardrails.enabled", matchIfMissing = true)
@EnableConfigurationProperties(GuardrailsTrifectaProperties.class)
public class GuardrailsTrifectaAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public SessionCapabilityPort sessionCapabilityPort(GuardrailsTrifectaProperties properties) {
    return new InMemorySessionCapabilityAdapter(
        properties.sessionIdleTimeout(), properties.sessionMaxDuration());
  }

  @Bean
  @ConditionalOnMissingBean({AssessTrifectaUseCase.class, ResetSessionUseCase.class})
  public AssessTrifectaService assessTrifectaService(
      SessionCapabilityPort sessionPort, GuardrailsTrifectaProperties properties) {
    return new AssessTrifectaService(sessionPort, properties.toPolicy());
  }

  /**
   * Derives the session from the MCP transport, falling back to the agent when the transport
   * carries none. Replace this bean if your deployment can identify a conversation more precisely.
   */
  @Bean
  @ConditionalOnMissingBean
  public SessionIdResolver sessionIdResolver() {
    return SessionIdResolver.mcpSessionOrAgent();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "mcp.guardrails.trifecta.enabled", matchIfMissing = true)
  public TrifectaGuardrail trifectaGuardrail(
      AssessTrifectaUseCase useCase, SessionIdResolver sessionIdResolver) {
    return new TrifectaGuardrail(useCase, sessionIdResolver);
  }
}
