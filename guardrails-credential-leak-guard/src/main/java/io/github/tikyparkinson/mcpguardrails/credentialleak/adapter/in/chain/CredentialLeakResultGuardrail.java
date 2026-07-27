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
package io.github.tikyparkinson.mcpguardrails.credentialleak.adapter.in.chain;

import io.github.tikyparkinson.mcpguardrails.core.application.port.out.ResultGuardrail;
import io.github.tikyparkinson.mcpguardrails.core.domain.Block;
import io.github.tikyparkinson.mcpguardrails.core.domain.PassThrough;
import io.github.tikyparkinson.mcpguardrails.core.domain.Redact;
import io.github.tikyparkinson.mcpguardrails.core.domain.ResultDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolResultContext;
import io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.in.RedactToolResultUseCase;
import io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.in.ResultRedaction;
import java.util.Objects;

/**
 * Outbound guardrail: keeps credentials returned by a tool out of the model's context.
 *
 * <p>Textual contents are redacted in place. A credential found in the structured content is always
 * blocked, whatever the configuration says: the outbound SPI exposes it read-only, so there is no
 * way to hand it back sanitized and letting it through would be a fail-open over a confirmed leak.
 */
public final class CredentialLeakResultGuardrail implements ResultGuardrail {

  public static final String GUARDRAIL_NAME = "credential-leak";

  private final RedactToolResultUseCase useCase;
  private final OutputAction onTextLeak;

  public CredentialLeakResultGuardrail(RedactToolResultUseCase useCase, OutputAction onTextLeak) {
    this.useCase = Objects.requireNonNull(useCase, "useCase");
    this.onTextLeak = Objects.requireNonNull(onTextLeak, "onTextLeak");
  }

  @Override
  public String name() {
    return GUARDRAIL_NAME;
  }

  @Override
  public ResultDecision inspect(ToolResultContext context) {
    ResultRedaction redaction = useCase.redact(context.textContents(), context.structuredContent());
    if (!redaction.structuredFindings().isEmpty()) {
      return new Block(
          "credential detected in structured result (%s); structured content cannot be redacted"
              .formatted(CredentialLeakGuardrail.describe(redaction.structuredFindings())));
    }
    if (redaction.textFindings().isEmpty()) {
      return new PassThrough();
    }
    String detail = CredentialLeakGuardrail.describe(redaction.textFindings());
    return switch (onTextLeak) {
      case REDACT -> new Redact(redaction.sanitizedContents(), detail);
      case BLOCK -> new Block("credential detected in tool result (%s)".formatted(detail));
    };
  }
}
