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

import io.github.tikyparkinson.mcpguardrails.approval.adapter.in.escalation.ApprovalGate;
import io.github.tikyparkinson.mcpguardrails.approval.adapter.out.channel.InMemoryApprovalRequestAdapter;
import io.github.tikyparkinson.mcpguardrails.approval.application.port.in.RequestApprovalUseCase;
import io.github.tikyparkinson.mcpguardrails.approval.application.port.in.ResolveApprovalUseCase;
import io.github.tikyparkinson.mcpguardrails.approval.application.port.out.ApprovalRequestPort;
import io.github.tikyparkinson.mcpguardrails.approval.application.usecase.RequestApprovalService;
import io.github.tikyparkinson.mcpguardrails.approval.infrastructure.GuardrailsApprovalProperties;
import io.github.tikyparkinson.mcpguardrails.core.application.port.out.EscalationResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires the human approval gate. This is what turns an {@code Escalate} verdict from an error
 * returned to the agent into a real pause.
 *
 * <p>{@link RequestApprovalService} implements both the requesting and the resolving side, so a
 * single instance is published under both types. Two instances would hold separate channels: a
 * person would resolve requests nobody is waiting on, and the invocation would time out beside it.
 *
 * <p>No transport is exposed. The starter publishes {@link ResolveApprovalUseCase} so the operator
 * can inject it into their own controller — and that endpoint decides who may release a blocked
 * invocation, so it needs protecting like one.
 */
@AutoConfiguration(after = GuardrailsCoreAutoConfiguration.class)
@ConditionalOnProperty(name = "mcp.guardrails.enabled", matchIfMissing = true)
@EnableConfigurationProperties(GuardrailsApprovalProperties.class)
public class GuardrailsApprovalAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ApprovalRequestPort approvalRequestPort(GuardrailsApprovalProperties properties) {
    return new InMemoryApprovalRequestAdapter(
        properties.maxPending(), properties.maxPendingPerAgent());
  }

  @Bean
  @ConditionalOnMissingBean({RequestApprovalUseCase.class, ResolveApprovalUseCase.class})
  public RequestApprovalService requestApprovalService(
      ApprovalRequestPort approvalPort, GuardrailsApprovalProperties properties) {
    return new RequestApprovalService(approvalPort, properties.toPolicy());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "mcp.guardrails.approval.enabled", matchIfMissing = true)
  public EscalationResolver approvalGate(RequestApprovalUseCase useCase) {
    return new ApprovalGate(useCase);
  }
}
