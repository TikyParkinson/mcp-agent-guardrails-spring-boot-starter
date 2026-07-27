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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tikyparkinson.mcpguardrails.audit.application.port.out.AuditLogStorePort;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEvent;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEventType;
import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolInvocationUseCase;
import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolResultUseCase;
import io.github.tikyparkinson.mcpguardrails.core.application.port.out.Guardrail;
import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolResultContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The acceptance criterion of F-1b in VALIDATION-0.2.0.md, checked against a real context rather
 * than against mocks: every guardrail that decides has to appear in the trail. The gap this closes
 * survived 921 unit tests precisely because nobody looked at the trail itself.
 */
class FullAuditCoverageTest {

  private static final String SECRET = "sk-proj-A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  GuardrailsCoreAutoConfiguration.class,
                  GuardrailsAuditAutoConfiguration.class,
                  GuardrailsToolIntegrityAutoConfiguration.class,
                  GuardrailsAuthzAutoConfiguration.class,
                  GuardrailsInjectionGuardAutoConfiguration.class,
                  GuardrailsCredentialLeakAutoConfiguration.class,
                  GuardrailsEgressAutoConfiguration.class,
                  GuardrailsAnomalyAutoConfiguration.class,
                  GuardrailsTrifectaAutoConfiguration.class,
                  GuardrailsApprovalAutoConfiguration.class,
                  GuardrailsRatelimitAutoConfiguration.class));

  @Test
  void shouldRecordEveryGuardrailThatDecidesWhenAnInvocationIsAllowed() {
    runner.run(
        context -> {
          // given the whole chain with nothing configured, so every guardrail permits
          Set<String> deciding =
              context.getBeansOfType(Guardrail.class).values().stream()
                  .map(Guardrail::name)
                  .filter(name -> !"audit".equals(name))
                  .collect(java.util.stream.Collectors.toSet());

          // when one invocation crosses it
          context.getBean(EvaluateToolInvocationUseCase.class).evaluate(invocation());

          // then each of them left its own event. Before this, five of nine were silent and a
          // blocked call read in the log exactly like an allowed one
          Set<String> audited =
              recent(context).stream()
                  .filter(event -> event.type() != AuditEventType.TOOL_INVOKED)
                  .map(AuditEvent::emittedBy)
                  .collect(java.util.stream.Collectors.toSet());
          assertEquals(deciding, audited);
        });
  }

  @Test
  void shouldRecordTheInvocationAndEveryDecisionWhenAnInvocationIsAllowed() {
    runner.run(
        context -> {
          // given the whole chain
          // when one invocation crosses it
          context.getBean(EvaluateToolInvocationUseCase.class).evaluate(invocation());

          // then the trail holds TOOL_INVOKED plus one decision per deciding guardrail. This is
          // the number the retention default is sized against
          List<AuditEvent> events = recent(context);
          assertEquals(9, events.size());
          assertEquals(
              1, events.stream().filter(e -> e.type() == AuditEventType.TOOL_INVOKED).count());
        });
  }

  @Test
  void shouldKeepTheMatchingRuleWhenAuthzAllows() {
    runner.run(
        context -> {
          // given an authz policy that permits by default
          // when an invocation crosses the chain
          context.getBean(EvaluateToolInvocationUseCase.class).evaluate(invocation());

          // then the rule that permitted is in the trail. authz used to publish this itself; the
          // point of removing that dependency was to keep the data while dropping the coupling
          AuditEvent authz =
              recent(context).stream()
                  .filter(event -> "authz".equals(event.emittedBy()))
                  .findFirst()
                  .orElseThrow();
          assertEquals("default", authz.detail());
        });
  }

  @Test
  void shouldRecordTheRedactionWithoutTheSecretWhenAToolReturnsOne() {
    runner.run(
        context -> {
          // given a tool response carrying a credential
          // when the outbound chain processes it
          context.getBean(EvaluateToolResultUseCase.class).evaluate(resultWithSecret());

          // then the redaction is in the trail, and the secret is not: the audit store must not
          // become the place where the redacted content ends up
          AuditEvent redaction =
              recent(context).stream()
                  .filter(event -> event.type() == AuditEventType.RESULT_REDACTED)
                  .findFirst()
                  .orElseThrow();
          assertFalse(redaction.detail().contains(SECRET));
        });
  }

  @Test
  void shouldPublishThePlainChainWhenTheAuditModuleIsAbsent() {
    // given a deployment without guardrails-audit wired at all
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                GuardrailsCoreAutoConfiguration.class, GuardrailsAuthzAutoConfiguration.class))
        .run(
            context -> {
              // when the chain is inspected
              // then it is the plain one. Auditing is observation and must never be a requirement
              // for deciding: without a bus the guardrails still protect the server
              assertFalse(
                  context.getBean(EvaluateToolInvocationUseCase.class)
                      instanceof AuditingEvaluateToolInvocation);
            });
  }

  @Test
  void shouldStillRecordDecisionsWhenOnlyTheAuditGuardrailIsTurnedOff() {
    runner
        .withPropertyValues("mcp.guardrails.audit.enabled=false")
        .run(
            context -> {
              // given the audit guardrail turned off, which is what that flag does — it removes the
              // guardrail from the chain, not the bus, exactly like every other module's flag
              // when an invocation crosses the chain
              context.getBean(EvaluateToolInvocationUseCase.class).evaluate(invocation());

              // then the decisions are still recorded and only TOOL_INVOKED is gone. Worth knowing
              // before an operator sets this expecting "stop auditing": to do that, drop
              // guardrails-audit from the classpath or use the master switch
              List<AuditEvent> events = recent(context);
              assertEquals(8, events.size());
              assertTrue(events.stream().noneMatch(e -> e.type() == AuditEventType.TOOL_INVOKED));
            });
  }

  @Test
  void shouldPublishTheAuditingDecoratorWhenAuditIsAvailable() {
    runner.run(
        context -> {
          // given the default setup
          // when the beans are inspected
          // then both chains are wrapped
          assertInstanceOf(
              AuditingEvaluateToolInvocation.class,
              context.getBean(EvaluateToolInvocationUseCase.class));
          assertInstanceOf(
              AuditingEvaluateToolResult.class, context.getBean(EvaluateToolResultUseCase.class));
        });
  }

  @Test
  void shouldStillGateEscalationsWhenTheAuditModuleIsAbsent() {
    // given approval-gate without guardrails-audit on the classpath
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                GuardrailsCoreAutoConfiguration.class, GuardrailsApprovalAutoConfiguration.class))
        .run(
            context -> {
              // when the context is inspected
              // then the escalation resolver is still there, undecorated. Auditing is an optional
              // observer: an operator who does not want an audit trail still gets human approval
              assertTrue(
                  context.getBeanNamesForType(
                              io.github.tikyparkinson.mcpguardrails.core.application.port.out
                                  .EscalationResolver.class)
                          .length
                      > 0);
            });
  }

  @Test
  void shouldKeepBothApprovalUseCasesInTheContextWhenDecorating() {
    runner.run(
        context -> {
          // given approval-gate wrapped for auditing
          // when the context is inspected
          // then the operator's side is still reachable. Decorating by replacing the bean would
          // have taken ResolveApprovalUseCase out of the context and broken their controller
          assertTrue(
              context.getBeanNamesForType(
                          io.github.tikyparkinson.mcpguardrails.approval.application.port.in
                              .ResolveApprovalUseCase.class)
                      .length
                  > 0);
        });
  }

  private static List<AuditEvent> recent(org.springframework.context.ApplicationContext context) {
    return context.getBean(AuditLogStorePort.class).findRecent(50);
  }

  private static ToolInvocationContext invocation() {
    return new ToolInvocationContext(
        new AgentId("agent-1"),
        new ToolName("search"),
        Instant.parse("2026-07-27T10:00:00Z"),
        Map.of("q", "sales report"),
        Map.of());
  }

  private static ToolResultContext resultWithSecret() {
    return new ToolResultContext(
        new AgentId("agent-1"),
        new ToolName("get_api_config"),
        Instant.parse("2026-07-27T10:00:00Z"),
        List.of("openai_key=" + SECRET),
        Map.of(),
        false);
  }
}
