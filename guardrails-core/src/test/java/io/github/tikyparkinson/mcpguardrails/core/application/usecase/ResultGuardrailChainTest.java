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

import io.github.tikyparkinson.mcpguardrails.core.application.port.out.ResultGuardrail;
import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.Block;
import io.github.tikyparkinson.mcpguardrails.core.domain.PassThrough;
import io.github.tikyparkinson.mcpguardrails.core.domain.Redact;
import io.github.tikyparkinson.mcpguardrails.core.domain.ResultDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ResultEvaluation;
import io.github.tikyparkinson.mcpguardrails.core.domain.ResultVerdict;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolResultContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ResultGuardrailChainTest {

  private static final ToolResultContext CONTEXT =
      new ToolResultContext(
          new AgentId("copilot"),
          new ToolName("read_file"),
          Instant.parse("2026-07-26T10:00:00Z"),
          List.of("token sk-live-1", "plain text"),
          Map.of(),
          false);

  private static ResultGuardrail guardrail(
      String name, int order, Function<ToolResultContext, ResultDecision> behaviour) {
    return new ResultGuardrail() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public int order() {
        return order;
      }

      @Override
      public ResultDecision inspect(ToolResultContext context) {
        return behaviour.apply(context);
      }
    };
  }

  private static ResultGuardrail passThrough(String name) {
    return guardrail(name, 0, context -> new PassThrough());
  }

  @Test
  void shouldPassThroughWhenNoGuardrailsAreRegistered() {
    // given: the extension must be neutral when nobody uses it
    ResultGuardrailChain chain = new ResultGuardrailChain(List.of());

    // when
    ResultVerdict verdict = chain.evaluate(CONTEXT);

    // then
    assertInstanceOf(PassThrough.class, verdict.finalDecision());
    assertEquals(List.of(), verdict.evaluations());
  }

  @Test
  void shouldEvaluateEveryGuardrailInOrderWhenChainRuns() {
    // given: order() first, then name() for ties
    List<String> calls = new ArrayList<>();
    ResultGuardrailChain chain =
        new ResultGuardrailChain(
            List.of(
                guardrail(
                    "zeta",
                    0,
                    context -> {
                      calls.add("zeta");
                      return new PassThrough();
                    }),
                guardrail(
                    "alpha",
                    0,
                    context -> {
                      calls.add("alpha");
                      return new PassThrough();
                    }),
                guardrail(
                    "early",
                    -10,
                    context -> {
                      calls.add("early");
                      return new PassThrough();
                    })));

    // when
    chain.evaluate(CONTEXT);

    // then
    assertEquals(List.of("early", "alpha", "zeta"), calls);
  }

  @Test
  void shouldComposeRedactionsInCascadeWhenSeveralGuardrailsRedact() {
    // given: the second guardrail must see what the first one already sanitized
    List<String> seenBySecond = new ArrayList<>();
    ResultGuardrailChain chain =
        new ResultGuardrailChain(
            List.of(
                guardrail(
                    "first",
                    1,
                    context -> new Redact(List.of("token ****", "plain text"), "api key")),
                guardrail(
                    "second",
                    2,
                    context -> {
                      seenBySecond.addAll(context.textContents());
                      return new Redact(List.of("token ****", "****"), "pii");
                    })));

    // when
    ResultVerdict verdict = chain.evaluate(CONTEXT);

    // then
    assertEquals(List.of("token ****", "plain text"), seenBySecond);
    Redact redact = assertInstanceOf(Redact.class, verdict.finalDecision());
    assertEquals(List.of("token ****", "****"), redact.sanitizedContents());
  }

  @Test
  void shouldEvaluateEveryGuardrailWhenOneBlocks() {
    // given: no short-circuit, so the trace stays complete
    List<String> calls = new ArrayList<>();
    ResultGuardrailChain chain =
        new ResultGuardrailChain(
            List.of(
                guardrail(
                    "blocker",
                    1,
                    context -> {
                      calls.add("blocker");
                      return new Block("secret");
                    }),
                guardrail(
                    "later",
                    2,
                    context -> {
                      calls.add("later");
                      return new PassThrough();
                    })));

    // when
    ResultVerdict verdict = chain.evaluate(CONTEXT);

    // then
    assertEquals(List.of("blocker", "later"), calls);
    assertEquals(2, verdict.evaluations().size());
    assertInstanceOf(Block.class, verdict.finalDecision());
  }

  @Test
  void shouldRecordEveryDecisionInTraceWhenChainRuns() {
    // given
    ResultGuardrailChain chain =
        new ResultGuardrailChain(
            List.of(
                passThrough("clean"),
                guardrail("leaky", 1, context -> new Redact(List.of("a", "b"), "api key"))));

    // when
    List<ResultEvaluation> evaluations = chain.evaluate(CONTEXT).evaluations();

    // then
    assertEquals(
        List.of("clean", "leaky"),
        evaluations.stream().map(ResultEvaluation::guardrailName).toList());
  }

  @Test
  void shouldBlockWhenGuardrailThrows() {
    // given: a scanner that explodes must not turn into an implicit pass
    ResultGuardrailChain chain =
        new ResultGuardrailChain(
            List.of(
                guardrail(
                    "broken",
                    0,
                    context -> {
                      throw new IllegalStateException("boom");
                    })));

    // when
    ResultVerdict verdict = chain.evaluate(CONTEXT);

    // then
    assertEquals(
        "outbound guardrail broken failed: IllegalStateException",
        assertInstanceOf(Block.class, verdict.finalDecision()).reason());
  }

  @Test
  void shouldBlockWhenGuardrailReturnsNull() {
    // given
    ResultGuardrailChain chain =
        new ResultGuardrailChain(List.of(guardrail("sloppy", 0, context -> null)));

    // when
    ResultVerdict verdict = chain.evaluate(CONTEXT);

    // then
    assertEquals(
        "outbound guardrail sloppy returned null",
        assertInstanceOf(Block.class, verdict.finalDecision()).reason());
  }

  @Test
  void shouldBlockWhenRedactionSizeDoesNotMatchContents() {
    // given: positional replacement is part of the contract; breaking it is fail-closed
    ResultGuardrailChain chain =
        new ResultGuardrailChain(
            List.of(
                guardrail("truncating", 0, context -> new Redact(List.of("only one"), "api key"))));

    // when
    ResultVerdict verdict = chain.evaluate(CONTEXT);

    // then
    assertEquals(
        "outbound guardrail truncating returned 1 contents, expected 2",
        assertInstanceOf(Block.class, verdict.finalDecision()).reason());
  }

  @Test
  void shouldNotApplyRedactionWhenSizeDoesNotMatch() {
    // given: the invalid redaction must not leak into what the next guardrail sees
    List<String> seenBySecond = new ArrayList<>();
    ResultGuardrailChain chain =
        new ResultGuardrailChain(
            List.of(
                guardrail("truncating", 1, context -> new Redact(List.of("only one"), "api key")),
                guardrail(
                    "second",
                    2,
                    context -> {
                      seenBySecond.addAll(context.textContents());
                      return new PassThrough();
                    })));

    // when
    chain.evaluate(CONTEXT);

    // then
    assertEquals(List.of("token sk-live-1", "plain text"), seenBySecond);
  }

  @Test
  void shouldFailWhenTwoGuardrailsShareTheSameName() {
    // given: duplicated names would make the trace ambiguous
    List<ResultGuardrail> duplicated =
        List.of(passThrough("credential-leak"), passThrough("credential-leak"));

    // when / then
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> new ResultGuardrailChain(duplicated));
    assertEquals("Duplicate outbound guardrail name: credential-leak", error.getMessage());
  }

  @Test
  void shouldRejectNullGuardrailListWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new ResultGuardrailChain(null));
  }

  @Test
  void shouldRejectNullContextWhenEvaluating() {
    // given
    ResultGuardrailChain chain = new ResultGuardrailChain(List.of());

    // when / then
    assertThrows(NullPointerException.class, () -> chain.evaluate(null));
  }

  @Test
  void shouldUseDefaultOrderWhenGuardrailDoesNotOverrideIt() {
    // given: the SPI default must be usable as-is
    ResultGuardrail minimal =
        new ResultGuardrail() {
          @Override
          public String name() {
            return "minimal";
          }

          @Override
          public ResultDecision inspect(ToolResultContext context) {
            return new PassThrough();
          }
        };

    // when / then
    assertEquals(0, minimal.order());
  }
}
