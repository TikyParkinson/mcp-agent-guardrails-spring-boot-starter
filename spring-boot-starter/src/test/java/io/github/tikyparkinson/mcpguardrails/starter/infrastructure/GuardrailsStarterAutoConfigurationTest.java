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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.audit.adapter.in.chain.AuditGuardrail;
import io.github.tikyparkinson.mcpguardrails.audit.adapter.out.persistence.InMemoryAuditLogStoreAdapter;
import io.github.tikyparkinson.mcpguardrails.audit.application.port.in.RecordAuditEventUseCase;
import io.github.tikyparkinson.mcpguardrails.audit.application.port.out.AuditLogStorePort;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEventType;
import io.github.tikyparkinson.mcpguardrails.authz.adapter.in.chain.AuthzGuardrail;
import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolInvocationUseCase;
import io.github.tikyparkinson.mcpguardrails.injectionguard.adapter.in.chain.InjectionGuardrail;
import io.github.tikyparkinson.mcpguardrails.ratelimit.adapter.in.chain.RateLimitGuardrail;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class GuardrailsStarterAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  GuardrailsCoreAutoConfiguration.class,
                  GuardrailsAuditAutoConfiguration.class,
                  GuardrailsAuthzAutoConfiguration.class,
                  GuardrailsInjectionGuardAutoConfiguration.class,
                  GuardrailsRatelimitAutoConfiguration.class));

  @Test
  void shouldRegisterChainAndAllFourGuardrailsWhenDefaults() {
    // given / when / then: zero-configuration promise
    runner.run(
        context -> {
          // the bean is the auditing decorator wrapping the chain — asserting the concrete type
          // would test how it is built rather than that it works
          assertNotNull(context.getBean(EvaluateToolInvocationUseCase.class));
          assertTrue(context.containsBean("auditGuardrail"));
          assertTrue(context.containsBean("authzGuardrail"));
          assertTrue(context.containsBean("injectionGuardrail"));
          assertTrue(context.containsBean("rateLimitGuardrail"));
          assertInstanceOf(
              InMemoryAuditLogStoreAdapter.class, context.getBean(AuditLogStorePort.class));
          assertTrue(context.containsBean("guardrailToolSpecificationPostProcessor"));
        });
  }

  @Test
  void shouldRegisterNothingWhenGloballyDisabled() {
    // given / when / then
    runner
        .withPropertyValues("mcp.guardrails.enabled=false")
        .run(
            context -> {
              assertFalse(context.containsBean("evaluateToolInvocationUseCase"));
              assertFalse(context.containsBean("auditGuardrail"));
              assertFalse(context.containsBean("authzGuardrail"));
              assertFalse(context.containsBean("injectionGuardrail"));
              assertFalse(context.containsBean("rateLimitGuardrail"));
            });
  }

  @Test
  void shouldSkipSingleGuardrailWhenItsFlagDisabled() {
    // given / when / then: audit off, but the bus stays for the other guardrails
    runner
        .withPropertyValues("mcp.guardrails.audit.enabled=false")
        .run(
            context -> {
              assertFalse(context.containsBean("auditGuardrail"));
              assertTrue(context.containsBean("recordAuditEventUseCase"));
              assertTrue(context.containsBean("authzGuardrail"));
            });
  }

  @Test
  void shouldBackOffWhenUserProvidesOwnStoreBean() {
    // given
    AuditLogStorePort customStore = mock(AuditLogStorePort.class);

    // when / then
    runner
        .withBean("myStore", AuditLogStorePort.class, () -> customStore)
        .run(
            context -> {
              assertEquals(customStore, context.getBean(AuditLogStorePort.class));
              assertFalse(context.containsBean("auditLogStorePort"));
            });
  }

  @Test
  void shouldOrderGuardrailsByOrderWhenChainBuilt() {
    // given / when / then: audit (-100), authz (0), injection-guard (50), ratelimit (100)
    runner.run(
        context -> {
          assertEquals(-100, context.getBean(AuditGuardrail.class).order());
          assertEquals(0, context.getBean(AuthzGuardrail.class).order());
          assertEquals(50, context.getBean(InjectionGuardrail.class).order());
          assertEquals(100, context.getBean(RateLimitGuardrail.class).order());
        });
  }

  @Test
  void shouldDenyDecoratedToolCallWhenAuthzDefaultIsDeny() {
    // given: default-deny posture + a user tool spec bean
    runner
        .withPropertyValues("mcp.guardrails.authz.default-effect=DENY")
        .withUserConfiguration(ToolSpecConfiguration.class)
        .run(
            context -> {
              McpServerFeatures.SyncToolSpecification spec =
                  context.getBean(McpServerFeatures.SyncToolSpecification.class);
              McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
              when(exchange.getClientInfo())
                  .thenReturn(new McpSchema.Implementation("test-agent", "1.0"));

              // when: invoking the (decorated) handler
              McpSchema.CallToolResult result =
                  spec.callHandler()
                      .apply(exchange, new McpSchema.CallToolRequest("echo", Map.of()));

              // then: tool blocked end-to-end and the decision is audited
              assertTrue(result.isError());
              assertTrue(
                  ((McpSchema.TextContent) result.content().get(0))
                      .text()
                      .contains("denied by guardrails"));
              assertTrue(
                  context.getBean(AuditLogStorePort.class).findRecent(10).stream()
                      .anyMatch(event -> event.type() == AuditEventType.DECISION_DENY));
            });
  }

  @Test
  void shouldExecuteDecoratedToolCallWhenDefaultsAllow() {
    // given: zero configuration — everything allows
    runner
        .withUserConfiguration(ToolSpecConfiguration.class)
        .run(
            context -> {
              McpServerFeatures.SyncToolSpecification spec =
                  context.getBean(McpServerFeatures.SyncToolSpecification.class);
              McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
              when(exchange.getClientInfo())
                  .thenReturn(new McpSchema.Implementation("test-agent", "1.0"));

              // when
              McpSchema.CallToolResult result =
                  spec.callHandler()
                      .apply(exchange, new McpSchema.CallToolRequest("echo", Map.of()));

              // then: the real tool ran and the invocation was audited
              assertEquals("tool ran", ((McpSchema.TextContent) result.content().get(0)).text());
              assertTrue(
                  context.getBean(AuditLogStorePort.class).findRecent(10).stream()
                      .anyMatch(event -> event.type() == AuditEventType.TOOL_INVOKED));
            });
  }

  @Test
  void shouldStillWireBusAndChainWhenAllGuardrailFlagsDisabled() {
    // given / when / then: empty chain allows everything but wiring stays intact
    runner
        .withPropertyValues(
            "mcp.guardrails.audit.enabled=false",
            "mcp.guardrails.authz.enabled=false",
            "mcp.guardrails.injection-guard.enabled=false",
            "mcp.guardrails.ratelimit.enabled=false")
        .run(
            context -> {
              assertTrue(context.containsBean("evaluateToolInvocationUseCase"));
              assertNotNull(context.getBean(RecordAuditEventUseCase.class));
              assertFalse(context.containsBean("auditGuardrail"));
              assertFalse(context.containsBean("rateLimitGuardrail"));
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class ToolSpecConfiguration {

    @Bean
    McpServerFeatures.SyncToolSpecification echoTool() {
      return McpServerFeatures.SyncToolSpecification.builder()
          .tool(McpSchema.Tool.builder("echo").build())
          .callHandler(
              (exchange, request) ->
                  McpSchema.CallToolResult.builder().addTextContent("tool ran").build())
          .build();
    }
  }
}
