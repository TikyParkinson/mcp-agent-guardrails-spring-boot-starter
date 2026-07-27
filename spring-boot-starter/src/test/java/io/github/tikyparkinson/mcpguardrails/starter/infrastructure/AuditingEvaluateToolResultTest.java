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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tikyparkinson.mcpguardrails.audit.application.port.in.RecordAuditEventUseCase;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEvent;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEventType;
import io.github.tikyparkinson.mcpguardrails.audit.domain.NewAuditEvent;
import io.github.tikyparkinson.mcpguardrails.core.application.port.in.EvaluateToolResultUseCase;
import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.github.tikyparkinson.mcpguardrails.core.domain.Block;
import io.github.tikyparkinson.mcpguardrails.core.domain.PassThrough;
import io.github.tikyparkinson.mcpguardrails.core.domain.Redact;
import io.github.tikyparkinson.mcpguardrails.core.domain.ResultEvaluation;
import io.github.tikyparkinson.mcpguardrails.core.domain.ResultVerdict;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolName;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolResultContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The outbound half: a redaction must leave the same kind of trace as a denial. */
class AuditingEvaluateToolResultTest {

  private static final String SECRET = "sk-proj-A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6";

  private static final ToolResultContext CONTEXT =
      new ToolResultContext(
          new AgentId("agent-1"),
          new ToolName("get_api_config"),
          Instant.parse("2026-07-27T10:00:00Z"),
          List.of("openai_key=" + SECRET),
          Map.of(),
          false);

  private final RecordingBus bus = new RecordingBus();

  @Test
  void shouldMapEachResultDecisionToItsEventTypeWhenRecording() {
    // given one guardrail per branch of ResultDecision
    EvaluateToolResultUseCase chain =
        context ->
            new ResultVerdict(
                new Block("stopped"),
                List.of(
                    new ResultEvaluation("a", new PassThrough()),
                    new ResultEvaluation("b", new Redact(List.of("clean"), "openai-api-key@text")),
                    new ResultEvaluation("c", new Block("stopped"))));

    // when the decorator records them
    new AuditingEvaluateToolResult(chain, bus).evaluate(CONTEXT);

    // then each branch has its own type, so a redaction is distinguishable from a clean pass
    assertEquals(
        List.of(
            AuditEventType.RESULT_PASS_THROUGH,
            AuditEventType.RESULT_REDACTED,
            AuditEventType.RESULT_BLOCKED),
        bus.events.stream().map(NewAuditEvent::type).toList());
  }

  @Test
  void shouldNeverRecordTheSanitizedContentWhenRedacting() {
    // given a redaction whose sanitized contents still hold the response text
    EvaluateToolResultUseCase chain =
        context ->
            new ResultVerdict(
                new Redact(List.of("openai_key=" + SECRET), "openai-api-key@result.text[0]"),
                List.of(
                    new ResultEvaluation(
                        "credential-leak",
                        new Redact(
                            List.of("openai_key=" + SECRET), "openai-api-key@result.text[0]"))));

    // when the decorator records it
    new AuditingEvaluateToolResult(chain, bus).evaluate(CONTEXT);

    // then the secret is not in the trail. Writing sanitizedContents here would put the very
    // content the redaction just removed into a store with a different access policy
    assertFalse(bus.events.get(0).detail().contains(SECRET));
  }

  @Test
  void shouldRecordTheReasonWhenRedacting() {
    // given a redaction that names the pattern it matched
    EvaluateToolResultUseCase chain =
        context ->
            new ResultVerdict(
                new PassThrough(),
                List.of(
                    new ResultEvaluation(
                        "credential-leak",
                        new Redact(List.of("clean"), "openai-api-key@result.text[0]"))));

    // when the decorator records it
    new AuditingEvaluateToolResult(chain, bus).evaluate(CONTEXT);

    // then the detail identifies what was found and where
    assertEquals("openai-api-key@result.text[0]", bus.events.get(0).detail());
  }

  @Test
  void shouldReturnTheVerdictUntouchedWhenRecording() {
    // given a verdict from the underlying chain
    ResultVerdict original =
        new ResultVerdict(
            new PassThrough(), List.of(new ResultEvaluation("credential-leak", new PassThrough())));

    // when the decorator evaluates
    ResultVerdict returned =
        new AuditingEvaluateToolResult(context -> original, bus).evaluate(CONTEXT);

    // then it is the very same verdict
    assertSame(original, returned);
  }

  @Test
  void shouldStillReturnTheResultWhenTheAuditBusFails() {
    // given an audit bus that is down
    RecordAuditEventUseCase broken =
        draft -> {
          throw new IllegalStateException("audit store down");
        };
    ResultVerdict verdict =
        new ResultVerdict(
            new PassThrough(), List.of(new ResultEvaluation("credential-leak", new PassThrough())));

    // when the decorator evaluates
    ResultVerdict returned =
        new AuditingEvaluateToolResult(context -> verdict, broken).evaluate(CONTEXT);

    // then the result still reaches the agent
    assertSame(verdict, returned);
  }

  @Test
  void shouldRejectNullCollaboratorsWhenConstructed() {
    // given
    EvaluateToolResultUseCase chain = context -> new ResultVerdict(new PassThrough(), List.of());

    // when / then
    assertThrows(NullPointerException.class, () -> new AuditingEvaluateToolResult(null, bus));
    assertThrows(NullPointerException.class, () -> new AuditingEvaluateToolResult(chain, null));
  }

  /** Captures drafts so a test can assert on what would have been persisted. */
  private static final class RecordingBus implements RecordAuditEventUseCase {
    private final List<NewAuditEvent> events = new ArrayList<>();

    @Override
    public AuditEvent publish(NewAuditEvent draft) {
      events.add(draft);
      return new AuditEvent(
          UUID.randomUUID(),
          draft.agentId(),
          draft.toolName(),
          Instant.parse("2026-07-27T10:00:00Z"),
          draft.emittedBy(),
          draft.type(),
          draft.detail());
    }
  }
}
