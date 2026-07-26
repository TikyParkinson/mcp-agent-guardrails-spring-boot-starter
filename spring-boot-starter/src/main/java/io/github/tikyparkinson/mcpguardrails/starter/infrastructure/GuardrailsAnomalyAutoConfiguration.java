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

import io.github.tikyparkinson.mcpguardrails.anomaly.adapter.in.chain.AnomalyGuardrail;
import io.github.tikyparkinson.mcpguardrails.anomaly.adapter.out.history.InMemoryInvocationHistoryAdapter;
import io.github.tikyparkinson.mcpguardrails.anomaly.application.port.in.DetectAnomalyUseCase;
import io.github.tikyparkinson.mcpguardrails.anomaly.application.port.out.InvocationHistoryPort;
import io.github.tikyparkinson.mcpguardrails.anomaly.application.usecase.DetectAnomalyService;
import io.github.tikyparkinson.mcpguardrails.anomaly.infrastructure.GuardrailsAnomalyProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires the detector of looping or compromised agents.
 *
 * <p>The default history lives in this process, so behind a load balancer each replica sees only
 * its own slice of an agent's behaviour. Publish an {@link InvocationHistoryPort} bean of your own
 * to correlate across a fleet.
 */
@AutoConfiguration(after = GuardrailsCoreAutoConfiguration.class)
@ConditionalOnProperty(name = "mcp.guardrails.enabled", matchIfMissing = true)
@EnableConfigurationProperties(GuardrailsAnomalyProperties.class)
public class GuardrailsAnomalyAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public InvocationHistoryPort invocationHistoryPort(GuardrailsAnomalyProperties properties) {
    return new InMemoryInvocationHistoryAdapter(
        properties.retention(), properties.maxRecordsPerAgent());
  }

  @Bean
  @ConditionalOnMissingBean
  public DetectAnomalyUseCase detectAnomalyUseCase(
      InvocationHistoryPort historyPort, GuardrailsAnomalyProperties properties) {
    return new DetectAnomalyService(historyPort, properties.toPolicy());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "mcp.guardrails.anomaly.enabled", matchIfMissing = true)
  public AnomalyGuardrail anomalyGuardrail(DetectAnomalyUseCase useCase) {
    return new AnomalyGuardrail(useCase);
  }
}
