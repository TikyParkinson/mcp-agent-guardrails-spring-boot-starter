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

import io.github.tikyparkinson.mcpguardrails.audit.adapter.in.chain.AuditGuardrail;
import io.github.tikyparkinson.mcpguardrails.audit.adapter.out.persistence.InMemoryAuditLogStoreAdapter;
import io.github.tikyparkinson.mcpguardrails.audit.application.port.in.RecordAuditEventUseCase;
import io.github.tikyparkinson.mcpguardrails.audit.application.port.out.AuditLogStorePort;
import io.github.tikyparkinson.mcpguardrails.audit.application.usecase.RecordAuditEventService;
import io.github.tikyparkinson.mcpguardrails.audit.infrastructure.GuardrailsAuditProperties;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Wires the audit guardrail: in-memory store by default, replaceable via the port. */
@AutoConfiguration(after = GuardrailsCoreAutoConfiguration.class)
@ConditionalOnProperty(name = "mcp.guardrails.enabled", matchIfMissing = true)
@EnableConfigurationProperties(GuardrailsAuditProperties.class)
public class GuardrailsAuditAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AuditLogStorePort auditLogStorePort(GuardrailsAuditProperties properties) {
    return new InMemoryAuditLogStoreAdapter(properties.inMemoryMaxEvents());
  }

  @Bean
  @ConditionalOnMissingBean
  public RecordAuditEventUseCase recordAuditEventUseCase(AuditLogStorePort store, Clock clock) {
    return new RecordAuditEventService(store, clock, UUID::randomUUID);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "mcp.guardrails.audit.enabled", matchIfMissing = true)
  public AuditGuardrail auditGuardrail(RecordAuditEventUseCase recordAuditEvent) {
    return new AuditGuardrail(recordAuditEvent);
  }
}
