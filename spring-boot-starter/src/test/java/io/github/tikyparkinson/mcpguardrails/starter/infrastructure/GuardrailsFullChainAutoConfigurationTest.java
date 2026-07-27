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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.approval.application.port.in.RequestApprovalUseCase;
import io.github.tikyparkinson.mcpguardrails.approval.application.port.in.ResolveApprovalUseCase;
import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolResultUseCase;
import io.github.tikyparkinson.mcpguardrails.core.application.port.out.EscalationResolver;
import io.github.tikyparkinson.mcpguardrails.core.application.port.out.Guardrail;
import io.github.tikyparkinson.mcpguardrails.core.application.port.out.ResultGuardrail;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port.out.ToolDefinitionCatalogPort;
import io.github.tikyparkinson.mcpguardrails.trifecta.application.port.in.AssessTrifectaUseCase;
import io.github.tikyparkinson.mcpguardrails.trifecta.application.port.in.ResetSessionUseCase;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The eleven modules assembled together, as a user importing the starter gets them. */
class GuardrailsFullChainAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  GuardrailsCoreAutoConfiguration.class,
                  GuardrailsAuditAutoConfiguration.class,
                  GuardrailsAuthzAutoConfiguration.class,
                  GuardrailsInjectionGuardAutoConfiguration.class,
                  GuardrailsRatelimitAutoConfiguration.class,
                  GuardrailsToolIntegrityAutoConfiguration.class,
                  GuardrailsCredentialLeakAutoConfiguration.class,
                  GuardrailsEgressAutoConfiguration.class,
                  GuardrailsAnomalyAutoConfiguration.class,
                  GuardrailsApprovalAutoConfiguration.class,
                  GuardrailsTrifectaAutoConfiguration.class));

  @Test
  void shouldOrderEveryGuardrailAsTheArchitectureDocuments() {
    // given the whole starter with no configuration at all
    runner.run(
        context -> {
          // when the chain is assembled
          List<String> chain =
              context.getBeansOfType(Guardrail.class).values().stream()
                  .sorted(Comparator.comparingInt(Guardrail::order))
                  .map(Guardrail::name)
                  .toList();

          // then the order matches ARCHITECTURE.md exactly. Order is the whole design here: audit
          // has to see everything, and rate limiting has to run last so a denied call still counts
          assertEquals(
              List.of(
                  "audit",
                  "tool-integrity",
                  "authz",
                  "injection-guard",
                  "credential-leak",
                  "egress-control",
                  "anomaly-detector",
                  "trifecta-correlator",
                  "ratelimit"),
              chain);
        });
  }

  @Test
  void shouldRunTheOutboundChainOnceAResultGuardrailExists() {
    // given the whole starter
    runner.run(
        context -> {
          // when the outbound side is inspected
          // then the chain exists and credential-leak is in it. Before this wiring the outbound
          // SPI was built and tested but never executed: results were never redacted
          assertNotNull(context.getBean(EvaluateToolResultUseCase.class));
          assertEquals(
              List.of("credential-leak"),
              context.getBeansOfType(ResultGuardrail.class).values().stream()
                  .map(ResultGuardrail::name)
                  .toList());
        });
  }

  @Test
  void shouldRegisterAnEscalationResolverSoEscalationsReachAPerson() {
    // given the whole starter
    runner.run(
        context -> {
          // when the escalation side is inspected
          // then approval-gate is wired as the resolver. Without it an escalation returns an error
          // to the agent, which is fail-closed but indistinguishable from a failure
          assertEquals(1, context.getBeansOfType(EscalationResolver.class).size());
        });
  }

  @Test
  void shouldFeedTheToolIntegrityCatalogFromTheDecoratedTools() {
    // given the whole starter
    runner.run(
        context -> {
          // when the registrar is inspected
          // then one exists, so definitions reach the catalog as tools are decorated. Without it
          // the guardrail would find no definition for any tool and decide on its unknown action
          assertEquals(1, context.getBeansOfType(ToolDefinitionRegistrar.class).size());
        });
  }

  @Test
  void shouldShareOneInstanceBetweenBothSidesOfApproval() {
    // given the whole starter
    runner.run(
        context -> {
          // when both approval ports are resolved
          // then they are the same object. Two instances would hold separate channels: a person
          // would resolve requests nobody waits on, and the invocation would time out beside it
          assertSame(
              context.getBean(RequestApprovalUseCase.class),
              context.getBean(ResolveApprovalUseCase.class));
        });
  }

  @Test
  void shouldShareOneInstanceBetweenBothSidesOfTrifecta() {
    // given the whole starter
    runner.run(
        context -> {
          // when both trifecta ports are resolved
          // then they are the same object, so resetting a session actually unblocks the one being
          // correlated
          assertSame(
              context.getBean(AssessTrifectaUseCase.class),
              context.getBean(ResetSessionUseCase.class));
        });
  }

  @Test
  void shouldRegisterEachDecoratedToolInTheIntegrityCatalog() {
    // given the whole starter and a tool as an MCP server would declare it
    runner.run(
        context -> {
          ToolDefinitionRegistrar registrar = context.getBean(ToolDefinitionRegistrar.class);
          ToolDefinitionCatalogPort catalog = context.getBean(ToolDefinitionCatalogPort.class);
          McpSchema.Tool tool =
              McpSchema.Tool.builder("read_customer")
                  .description("Reads a customer record")
                  .build();

          // when the post-processor hands it over, as it does while decorating
          registrar.register(tool);

          // then the guardrail can find that definition later. Without this the catalog stays
          // empty and every invocation looks like a tool nobody ever saw
          assertTrue(catalog.findByName("read_customer").isPresent());
        });
  }

  @Test
  void shouldWarnAtStartUpWhenEscalationsHaveNowhereToGo() {
    // given a starter with escalating guardrails but no approval gate
    runner
        .withPropertyValues("mcp.guardrails.approval.enabled=false")
        .run(
            context -> {
              // when the start-up runner executes, as Spring Boot does once the context is ready
              ApplicationRunner warnings = context.getBean(ApplicationRunner.class);

              // then it completes without failing the boot: a warning must never stop the server,
              // only tell the operator what is not protecting them
              assertDoesNotThrow(() -> warnings.run(new DefaultApplicationArguments()));
            });
  }

  @Test
  void shouldRunTheStartUpCheckQuietlyWhenEverythingIsWired() {
    // given the whole starter with the approval gate in place
    runner.run(
        context -> {
          // when the start-up runner executes
          ApplicationRunner warnings = context.getBean(ApplicationRunner.class);

          // then it completes with nothing to report
          assertDoesNotThrow(() -> warnings.run(new DefaultApplicationArguments()));
        });
  }

  @Test
  void shouldRegisterNothingWhenTheMasterSwitchIsOff() {
    // given the master switch turned off
    runner
        .withPropertyValues("mcp.guardrails.enabled=false")
        .run(
            context -> {
              // when the context is inspected
              // then no guardrail and no post-processor exist, so tools are never decorated and
              // nothing is evaluated at all
              assertTrue(context.getBeansOfType(Guardrail.class).isEmpty());
              assertFalse(context.containsBean("guardrailToolSpecificationPostProcessor"));
            });
  }

  @Test
  void shouldDropOnlyTheGuardrailsTurnedOffIndividually() {
    // given two modules disabled by their own property
    runner
        .withPropertyValues(
            "mcp.guardrails.trifecta.enabled=false", "mcp.guardrails.anomaly.enabled=false")
        .run(
            context -> {
              // when the chain is inspected
              List<String> names =
                  context.getBeansOfType(Guardrail.class).values().stream()
                      .map(Guardrail::name)
                      .sorted()
                      .toList();

              // then the other seven stay: each module's switch is its own, and turning one off
              // must not take the rest with it
              assertEquals(7, names.size());
              assertFalse(names.contains("trifecta-correlator"));
              assertFalse(names.contains("anomaly-detector"));
            });
  }

  @Test
  void shouldStopFeedingTheCatalogWhenToolIntegrityIsOff() {
    // given tool-integrity turned off
    runner
        .withPropertyValues("mcp.guardrails.tool-integrity.enabled=false")
        .run(
            context -> {
              // when the registrar is inspected
              // then nothing records definitions: the registrar exists only for the guardrail that
              // asked for them
              assertTrue(context.getBeansOfType(ToolDefinitionRegistrar.class).isEmpty());
            });
  }

  @Test
  void shouldStopResolvingEscalationsWhenApprovalIsOff() {
    // given approval-gate turned off
    runner
        .withPropertyValues("mcp.guardrails.approval.enabled=false")
        .run(
            context -> {
              // when the escalation side is inspected
              // then no resolver is registered and escalations fall back to the historical error
              assertTrue(context.getBeansOfType(EscalationResolver.class).isEmpty());
            });
  }

  @Test
  void shouldKeepTheOutboundChainWorkingWithNoResultGuardrail() {
    // given credential-leak turned off, which leaves no outbound guardrail at all
    runner
        .withPropertyValues("mcp.guardrails.credential-leak.enabled=false")
        .run(
            context -> {
              // when the outbound chain is inspected
              // then it still exists and holds nothing. An empty chain answers PassThrough, so
              // wiring it changes nothing for a deployment that redacts no results
              assertTrue(context.getBeansOfType(ResultGuardrail.class).isEmpty());
              assertNotNull(context.getBean(EvaluateToolResultUseCase.class));
            });
  }
}
