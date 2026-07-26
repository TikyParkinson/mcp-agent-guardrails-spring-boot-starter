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
import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolResultUseCase;
import io.github.tikyparkinson.mcpguardrails.core.application.port.out.EscalationResolver;
import io.github.tikyparkinson.mcpguardrails.core.application.port.out.Guardrail;
import io.github.tikyparkinson.mcpguardrails.core.application.port.out.ResultGuardrail;
import io.github.tikyparkinson.mcpguardrails.core.application.usecase.GuardrailChain;
import io.github.tikyparkinson.mcpguardrails.core.application.usecase.ResultGuardrailChain;
import io.github.tikyparkinson.mcpguardrails.core.infrastructure.GuardrailsCoreProperties;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
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

  private static final Logger LOG = LoggerFactory.getLogger(GuardrailsCoreAutoConfiguration.class);

  /**
   * Guardrails that can return {@code Escalate} under some configuration. Kept as names rather than
   * types so this class does not depend on every module: the starter can, but making the core
   * wiring know each guardrail would defeat the SPI.
   */
  private static final Set<String> ESCALATION_CAPABLE =
      Set.of(
          "authz",
          "injection-guard",
          "credential-leak",
          "egress-control",
          "anomaly-detector",
          "trifecta-correlator");

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

  /**
   * The outbound chain, which inspects what a tool returns. With no {@link ResultGuardrail}
   * registered the combined decision is {@code PassThrough}, so wiring this changes nothing for a
   * deployment that has none.
   */
  @Bean
  @ConditionalOnMissingBean
  public EvaluateToolResultUseCase evaluateToolResultUseCase(List<ResultGuardrail> guardrails) {
    return new ResultGuardrailChain(guardrails);
  }

  /** Static: BeanPostProcessors must not trigger early initialization of the context. */
  @Bean
  @ConditionalOnMissingBean
  public static GuardrailToolSpecificationPostProcessor guardrailToolSpecificationPostProcessor(
      ObjectProvider<EvaluateToolInvocationUseCase> useCase,
      ObjectProvider<EvaluateToolResultUseCase> resultUseCase,
      ObjectProvider<AgentIdResolver> agentIdResolver,
      ObjectProvider<Clock> clock,
      ObjectProvider<EscalationResolver> escalationResolver,
      ObjectProvider<ToolDefinitionRegistrar> definitionRegistrar) {
    return new GuardrailToolSpecificationPostProcessor(
        useCase, resultUseCase, agentIdResolver, clock, escalationResolver, definitionRegistrar);
  }

  /**
   * Reports at start-up what the assembled wiring cannot do — today, guardrails that can escalate
   * with nothing to resolve the escalation. Runs once the context is ready so it sees the final set
   * of beans, and only logs: a warning must never be a reason not to start.
   */
  @Bean
  @ConditionalOnMissingBean
  public ApplicationRunner guardrailsStartupWarningsRunner(
      List<Guardrail> guardrails, ObjectProvider<EscalationResolver> escalationResolver) {
    return args ->
        StarterStartupWarnings.of(escalationResolver.getIfAvailable(), escalatingNames(guardrails))
            .forEach(LOG::warn);
  }

  /**
   * Guardrails whose configuration allows an {@code Escalate}. Read from each one's own name rather
   * than by evaluating them: asking a guardrail to decide at start-up would need an invocation that
   * does not exist yet.
   */
  private static List<String> escalatingNames(List<Guardrail> guardrails) {
    return guardrails.stream()
        .map(Guardrail::name)
        .filter(ESCALATION_CAPABLE::contains)
        .sorted()
        .toList();
  }
}
