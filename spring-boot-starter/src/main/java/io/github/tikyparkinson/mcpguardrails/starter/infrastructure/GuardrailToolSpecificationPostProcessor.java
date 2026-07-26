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
import io.github.tikyparkinson.mcpguardrails.core.adapter.in.mcp.GuardrailToolDecorator;
import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolInvocationUseCase;
import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolResultUseCase;
import io.github.tikyparkinson.mcpguardrails.core.application.port.out.EscalationResolver;
import io.modelcontextprotocol.server.McpServerFeatures;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Decorates MCP tool specification beans so every tool call runs behind the guardrail chain.
 * Handles both single {@link McpServerFeatures.SyncToolSpecification} beans and {@code List} beans
 * whose elements are all tool specifications (the common Spring AI MCP server pattern).
 *
 * <p>Every tool gets the whole set: the inbound chain, the outbound chain over its result, and the
 * escalation resolver when one is registered. The last two are looked up lazily through {@link
 * ObjectProvider}, so a deployment that wires neither behaves exactly as before.
 */
public final class GuardrailToolSpecificationPostProcessor implements BeanPostProcessor {

  private final ObjectProvider<EvaluateToolInvocationUseCase> useCase;
  private final ObjectProvider<EvaluateToolResultUseCase> resultUseCase;
  private final ObjectProvider<AgentIdResolver> agentIdResolver;
  private final ObjectProvider<Clock> clock;
  private final ObjectProvider<EscalationResolver> escalationResolver;
  private final ObjectProvider<ToolDefinitionRegistrar> definitionRegistrar;

  public GuardrailToolSpecificationPostProcessor(
      ObjectProvider<EvaluateToolInvocationUseCase> useCase,
      ObjectProvider<EvaluateToolResultUseCase> resultUseCase,
      ObjectProvider<AgentIdResolver> agentIdResolver,
      ObjectProvider<Clock> clock,
      ObjectProvider<EscalationResolver> escalationResolver,
      ObjectProvider<ToolDefinitionRegistrar> definitionRegistrar) {
    this.useCase = Objects.requireNonNull(useCase, "useCase");
    this.resultUseCase = Objects.requireNonNull(resultUseCase, "resultUseCase");
    this.agentIdResolver = Objects.requireNonNull(agentIdResolver, "agentIdResolver");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.escalationResolver = Objects.requireNonNull(escalationResolver, "escalationResolver");
    this.definitionRegistrar = Objects.requireNonNull(definitionRegistrar, "definitionRegistrar");
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof McpServerFeatures.SyncToolSpecification spec) {
      return decorate(spec);
    }
    if (bean instanceof List<?> list && isToolSpecificationList(list)) {
      List<Object> decorated = new ArrayList<>(list.size());
      for (Object element : list) {
        decorated.add(decorate((McpServerFeatures.SyncToolSpecification) element));
      }
      return List.copyOf(decorated);
    }
    return bean;
  }

  /**
   * Records the tool's definition and returns it guarded. Registration happens here, as the tool is
   * decorated, so no invocation can arrive before the definition is known.
   */
  private McpServerFeatures.SyncToolSpecification decorate(
      McpServerFeatures.SyncToolSpecification spec) {
    definitionRegistrar.getIfAvailable(ToolDefinitionRegistrar::noOp).register(spec.tool());
    return GuardrailToolDecorator.decorate(
        spec,
        useCase.getObject(),
        resultUseCase.getObject(),
        agentIdResolver.getObject(),
        clock.getObject(),
        escalationResolver.getIfAvailable());
  }

  private static boolean isToolSpecificationList(List<?> list) {
    return !list.isEmpty()
        && list.stream().allMatch(e -> e instanceof McpServerFeatures.SyncToolSpecification);
  }
}
