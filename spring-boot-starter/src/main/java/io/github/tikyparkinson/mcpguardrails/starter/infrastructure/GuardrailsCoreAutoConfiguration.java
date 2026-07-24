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

import io.github.tikyparkinson.mcpguardrails.core.adapter.in.mcp.AgentIdResolver;
import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolInvocationUseCase;
import io.github.tikyparkinson.mcpguardrails.core.application.port.out.Guardrail;
import io.github.tikyparkinson.mcpguardrails.core.application.usecase.GuardrailChain;
import io.github.tikyparkinson.mcpguardrails.core.infrastructure.GuardrailsCoreProperties;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires the guardrail chain and the MCP tool decoration. Every bean is
 * {@code @ConditionalOnMissingBean} so users can replace any piece by exposing their own.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "mcp.guardrails.enabled", matchIfMissing = true)
@EnableConfigurationProperties(GuardrailsCoreProperties.class)
public class GuardrailsCoreAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public Clock mcpGuardrailsClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentIdResolver agentIdResolver() {
    return AgentIdResolver.clientInfoName();
  }

  @Bean
  @ConditionalOnMissingBean
  public EvaluateToolInvocationUseCase evaluateToolInvocationUseCase(List<Guardrail> guardrails) {
    return new GuardrailChain(guardrails);
  }

  /** Static: BeanPostProcessors must not trigger early initialization of the context. */
  @Bean
  @ConditionalOnMissingBean
  public static GuardrailToolSpecificationPostProcessor guardrailToolSpecificationPostProcessor(
      ObjectProvider<EvaluateToolInvocationUseCase> useCase,
      ObjectProvider<AgentIdResolver> agentIdResolver,
      ObjectProvider<Clock> clock) {
    return new GuardrailToolSpecificationPostProcessor(useCase, agentIdResolver, clock);
  }
}
