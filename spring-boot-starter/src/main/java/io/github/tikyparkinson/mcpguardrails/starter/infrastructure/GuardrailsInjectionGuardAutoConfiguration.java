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

import io.github.tikyparkinson.mcpguardrails.injectionguard.adapter.in.chain.InjectionGuardrail;
import io.github.tikyparkinson.mcpguardrails.injectionguard.adapter.out.rules.InMemoryInjectionRuleSetAdapter;
import io.github.tikyparkinson.mcpguardrails.injectionguard.application.port.in.ScanToolArgumentsUseCase;
import io.github.tikyparkinson.mcpguardrails.injectionguard.application.port.out.InjectionRuleSetPort;
import io.github.tikyparkinson.mcpguardrails.injectionguard.application.usecase.ScanToolArgumentsService;
import io.github.tikyparkinson.mcpguardrails.injectionguard.infrastructure.GuardrailsInjectionGuardProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Wires the injection guardrail: built-in + configured rules by default. */
@AutoConfiguration(after = GuardrailsAuditAutoConfiguration.class)
@ConditionalOnProperty(name = "mcp.guardrails.enabled", matchIfMissing = true)
@EnableConfigurationProperties(GuardrailsInjectionGuardProperties.class)
public class GuardrailsInjectionGuardAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public InjectionRuleSetPort injectionRuleSetPort(GuardrailsInjectionGuardProperties properties) {
    return new InMemoryInjectionRuleSetAdapter(properties.toRules());
  }

  @Bean
  @ConditionalOnMissingBean
  public ScanToolArgumentsUseCase scanToolArgumentsUseCase(
      InjectionRuleSetPort ruleSetPort, GuardrailsInjectionGuardProperties properties) {
    return new ScanToolArgumentsService(ruleSetPort, properties.toBudget());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "mcp.guardrails.injection-guard.enabled", matchIfMissing = true)
  public InjectionGuardrail injectionGuardrail(ScanToolArgumentsUseCase scanArguments) {
    return new InjectionGuardrail(scanArguments);
  }
}
