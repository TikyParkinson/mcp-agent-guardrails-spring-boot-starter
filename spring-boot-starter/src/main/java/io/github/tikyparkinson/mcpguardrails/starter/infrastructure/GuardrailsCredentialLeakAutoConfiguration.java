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

import io.github.tikyparkinson.mcpguardrails.credentialleak.adapter.in.chain.CredentialLeakGuardrail;
import io.github.tikyparkinson.mcpguardrails.credentialleak.adapter.in.chain.CredentialLeakResultGuardrail;
import io.github.tikyparkinson.mcpguardrails.credentialleak.adapter.out.patterns.InMemorySecretPatternSetAdapter;
import io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.in.RedactToolResultUseCase;
import io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.in.ScanToolArgumentsForSecretsUseCase;
import io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.out.SecretPatternSetPort;
import io.github.tikyparkinson.mcpguardrails.credentialleak.application.usecase.RedactToolResultService;
import io.github.tikyparkinson.mcpguardrails.credentialleak.application.usecase.ScanToolArgumentsForSecretsService;
import io.github.tikyparkinson.mcpguardrails.credentialleak.infrastructure.GuardrailsCredentialLeakProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires both halves of the credential leak guardrail: the inbound one, which sees secrets an agent
 * sends to a tool, and the outbound one, which redacts secrets a tool returns.
 *
 * <p>The outbound half only does anything because {@code GuardrailsCoreAutoConfiguration} now
 * publishes the outbound chain and the post-processor passes it on. Wiring this guardrail without
 * that would advertise redaction of tool results and never perform it.
 */
@AutoConfiguration(after = GuardrailsCoreAutoConfiguration.class)
@ConditionalOnProperty(name = "mcp.guardrails.enabled", matchIfMissing = true)
@EnableConfigurationProperties(GuardrailsCredentialLeakProperties.class)
public class GuardrailsCredentialLeakAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public SecretPatternSetPort secretPatternSetPort(GuardrailsCredentialLeakProperties properties) {
    return new InMemorySecretPatternSetAdapter(properties.toPatterns());
  }

  @Bean
  @ConditionalOnMissingBean
  public ScanToolArgumentsForSecretsUseCase scanToolArgumentsForSecretsUseCase(
      SecretPatternSetPort patternSetPort) {
    return new ScanToolArgumentsForSecretsService(patternSetPort);
  }

  @Bean
  @ConditionalOnMissingBean
  public RedactToolResultUseCase redactToolResultUseCase(SecretPatternSetPort patternSetPort) {
    return new RedactToolResultService(patternSetPort);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "mcp.guardrails.credential-leak.enabled", matchIfMissing = true)
  public CredentialLeakGuardrail credentialLeakGuardrail(
      ScanToolArgumentsForSecretsUseCase useCase, GuardrailsCredentialLeakProperties properties) {
    return new CredentialLeakGuardrail(
        useCase, properties.onConfirmedInput(), properties.onSuspectedInput());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "mcp.guardrails.credential-leak.enabled", matchIfMissing = true)
  public CredentialLeakResultGuardrail credentialLeakResultGuardrail(
      RedactToolResultUseCase useCase, GuardrailsCredentialLeakProperties properties) {
    return new CredentialLeakResultGuardrail(useCase, properties.onOutputText());
  }
}
