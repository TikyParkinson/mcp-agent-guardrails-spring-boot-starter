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

import io.github.tikyparkinson.mcpguardrails.ratelimit.adapter.in.chain.RateLimitGuardrail;
import io.github.tikyparkinson.mcpguardrails.ratelimit.adapter.out.persistence.InMemoryRateLimitStoreAdapter;
import io.github.tikyparkinson.mcpguardrails.ratelimit.application.port.in.CheckRateLimitUseCase;
import io.github.tikyparkinson.mcpguardrails.ratelimit.application.port.out.RateLimitStorePort;
import io.github.tikyparkinson.mcpguardrails.ratelimit.application.usecase.CheckRateLimitService;
import io.github.tikyparkinson.mcpguardrails.ratelimit.infrastructure.GuardrailsRatelimitProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Wires the rate limit guardrail: in-memory counters by default, replaceable via the port. */
@AutoConfiguration(after = GuardrailsAuditAutoConfiguration.class)
@ConditionalOnProperty(name = "mcp.guardrails.enabled", matchIfMissing = true)
@EnableConfigurationProperties(GuardrailsRatelimitProperties.class)
public class GuardrailsRatelimitAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public RateLimitStorePort rateLimitStorePort() {
    return new InMemoryRateLimitStoreAdapter();
  }

  @Bean
  @ConditionalOnMissingBean
  public CheckRateLimitUseCase checkRateLimitUseCase(
      RateLimitStorePort store, GuardrailsRatelimitProperties properties) {
    return new CheckRateLimitService(store, properties.toPolicy());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "mcp.guardrails.ratelimit.enabled", matchIfMissing = true)
  public RateLimitGuardrail rateLimitGuardrail(CheckRateLimitUseCase checkRateLimit) {
    return new RateLimitGuardrail(checkRateLimit);
  }
}
