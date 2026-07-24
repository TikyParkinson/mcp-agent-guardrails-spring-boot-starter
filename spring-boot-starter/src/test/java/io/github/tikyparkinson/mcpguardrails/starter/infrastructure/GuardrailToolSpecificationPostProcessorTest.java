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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.core.adapter.in.mcp.AgentIdResolver;
import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolInvocationUseCase;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.ChainVerdict;
import io.modelcontextprotocol.server.McpServerFeatures;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class GuardrailToolSpecificationPostProcessorTest {

  private final EvaluateToolInvocationUseCase useCase =
      context -> new ChainVerdict(new Allow(), List.of());
  private final GuardrailToolSpecificationPostProcessor processor =
      new GuardrailToolSpecificationPostProcessor(
          provider(useCase),
          provider(AgentIdResolver.clientInfoName()),
          provider(Clock.systemUTC()));

  @Test
  void shouldDecorateSingleToolSpecificationWhenBeanMatches() {
    // given
    McpServerFeatures.SyncToolSpecification original = toolSpec("alpha");

    // when
    Object processed = processor.postProcessAfterInitialization(original, "alpha");

    // then: new spec, same tool, wrapped handler
    McpServerFeatures.SyncToolSpecification decorated =
        assertInstanceOf(McpServerFeatures.SyncToolSpecification.class, processed);
    assertNotSame(original, decorated);
    assertSame(original.tool(), decorated.tool());
  }

  @Test
  void shouldDecorateEveryElementWhenListOfToolSpecifications() {
    // given
    List<McpServerFeatures.SyncToolSpecification> original =
        List.of(toolSpec("alpha"), toolSpec("beta"));

    // when
    Object processed = processor.postProcessAfterInitialization(original, "tools");

    // then
    List<?> decorated = assertInstanceOf(List.class, processed);
    assertEquals(2, decorated.size());
    for (int i = 0; i < 2; i++) {
      assertNotSame(original.get(i), decorated.get(i));
      assertSame(
          original.get(i).tool(),
          ((McpServerFeatures.SyncToolSpecification) decorated.get(i)).tool());
    }
  }

  @Test
  void shouldLeaveEmptyListUntouchedWhenProcessed() {
    // given
    List<Object> empty = List.of();

    // when / then
    assertSame(empty, processor.postProcessAfterInitialization(empty, "empty"));
  }

  @Test
  void shouldLeaveMixedListUntouchedWhenNotAllElementsAreToolSpecs() {
    // given
    List<Object> mixed = List.of(toolSpec("alpha"), "not a tool spec");

    // when / then
    assertSame(mixed, processor.postProcessAfterInitialization(mixed, "mixed"));
  }

  @Test
  void shouldLeaveUnrelatedBeanUntouchedWhenProcessed() {
    // given
    Object unrelated = new Object();

    // when / then
    assertSame(unrelated, processor.postProcessAfterInitialization(unrelated, "other"));
  }

  private static McpServerFeatures.SyncToolSpecification toolSpec(String name) {
    return McpServerFeatures.SyncToolSpecification.builder()
        .tool(io.modelcontextprotocol.spec.McpSchema.Tool.builder(name).build())
        .callHandler(
            (exchange, request) ->
                io.modelcontextprotocol.spec.McpSchema.CallToolResult.builder()
                    .addTextContent("ran")
                    .build())
        .build();
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> provider(T instance) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getObject()).thenReturn(instance);
    return provider;
  }
}
