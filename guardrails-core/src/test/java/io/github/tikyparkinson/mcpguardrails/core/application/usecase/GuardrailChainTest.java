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
package io.github.tikyparkinson.mcpguardrails.core.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tikyparkinson.mcpguardrails.core.application.port.out.Guardrail;
import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.ChainVerdict;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class GuardrailChainTest {

  private static final ToolInvocationContext CONTEXT =
      new ToolInvocationContext(
          new AgentId("agent-1"),
          new ToolName("search"),
          Instant.parse("2026-07-24T10:00:00Z"),
          Map.of(),
          Map.of());

  @Test
  void shouldAllowWhenChainIsEmpty() {
    // given
    GuardrailChain chain = new GuardrailChain(List.of());

    // when
    ChainVerdict verdict = chain.evaluate(CONTEXT);

    // then
    assertEquals(new Allow(), verdict.finalDecision());
    assertEquals(List.of(), verdict.evaluations());
  }

  @Test
  void shouldEvaluateAllGuardrailsWhenOneDenies() {
    // given: no short-circuit, every guardrail must appear in the trace
    GuardrailChain chain =
        new GuardrailChain(
            List.of(
                guardrail("a", 0, ctx -> new Deny("nope")), guardrail("b", 1, ctx -> new Allow())));

    // when
    ChainVerdict verdict = chain.evaluate(CONTEXT);

    // then
    assertEquals(new Deny("nope"), verdict.finalDecision());
    assertEquals(2, verdict.evaluations().size());
    assertEquals("b", verdict.evaluations().get(1).guardrailName());
  }

  @Test
  void shouldEvaluateInOrderThenNameWhenOrdersTie() {
    // given: same order() -> alphabetical by name; lower order() runs first
    GuardrailChain chain =
        new GuardrailChain(
            List.of(
                guardrail("zeta", 0, ctx -> new Allow()),
                guardrail("alpha", 0, ctx -> new Allow()),
                guardrail("early", -1, ctx -> new Allow())));

    // when
    ChainVerdict verdict = chain.evaluate(CONTEXT);

    // then
    assertEquals(
        List.of("early", "alpha", "zeta"),
        verdict.evaluations().stream().map(e -> e.guardrailName()).toList());
  }

  @Test
  void shouldEscalateWhenNoDenyButOneEscalates() {
    // given
    GuardrailChain chain =
        new GuardrailChain(
            List.of(
                guardrail("a", 0, ctx -> new Allow()),
                guardrail("b", 1, ctx -> new Escalate("needs human"))));

    // when / then
    assertEquals(new Escalate("needs human"), chain.evaluate(CONTEXT).finalDecision());
  }

  @Test
  void shouldDenyWhenGuardrailThrowsUnexpectedException() {
    // given: fail-closed rule
    GuardrailChain chain =
        new GuardrailChain(
            List.of(
                guardrail(
                    "broken",
                    0,
                    ctx -> {
                      throw new IllegalStateException("boom");
                    })));

    // when
    ChainVerdict verdict = chain.evaluate(CONTEXT);

    // then
    Deny deny = assertInstanceOf(Deny.class, verdict.finalDecision());
    assertEquals("guardrail broken failed: IllegalStateException", deny.reason());
  }

  @Test
  void shouldDenyWhenGuardrailReturnsNull() {
    // given: null decision is a contract violation -> fail-closed
    GuardrailChain chain = new GuardrailChain(List.of(guardrail("nullish", 0, ctx -> null)));

    // when / then
    assertInstanceOf(Deny.class, chain.evaluate(CONTEXT).finalDecision());
  }

  @Test
  void shouldRejectDuplicateGuardrailNamesWhenConstructed() {
    // given
    List<Guardrail> duplicated =
        List.of(guardrail("same", 0, ctx -> new Allow()), guardrail("same", 1, ctx -> new Allow()));

    // when / then
    assertThrows(IllegalArgumentException.class, () -> new GuardrailChain(duplicated));
  }

  @Test
  void shouldRejectNullContextWhenEvaluating() {
    // given
    GuardrailChain chain = new GuardrailChain(List.of());

    // when / then
    assertThrows(NullPointerException.class, () -> chain.evaluate(null));
  }

  @Test
  void shouldDefaultToOrderZeroWhenGuardrailDoesNotOverrideIt() {
    // given: SPI contract — order() defaults to 0
    Guardrail minimal =
        new Guardrail() {
          @Override
          public String name() {
            return "minimal";
          }

          @Override
          public GuardrailDecision evaluate(ToolInvocationContext context) {
            return new Allow();
          }
        };

    // when / then
    assertEquals(0, minimal.order());
  }

  private static Guardrail guardrail(
      String name, int order, Function<ToolInvocationContext, GuardrailDecision> fn) {
    return new Guardrail() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public int order() {
        return order;
      }

      @Override
      public GuardrailDecision evaluate(ToolInvocationContext context) {
        return fn.apply(context);
      }
    };
  }
}
