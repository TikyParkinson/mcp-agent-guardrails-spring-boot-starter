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
package io.github.tikyparkinson.mcpguardrails.credentialleak.application.usecase;

import io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.in.RedactToolResultUseCase;
import io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.in.ResultRedaction;
import io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.out.SecretPatternSetPort;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.RedactedText;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretFinding;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretPattern;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretRedactor;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretScanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sanitizes the result of an invocation: redacts its texts and scans its structured content, which
 * cannot be rewritten.
 */
public final class RedactToolResultService implements RedactToolResultUseCase {

  private static final String STRUCTURED_PREFIX = "result.structured";

  private final SecretPatternSetPort patternSetPort;

  public RedactToolResultService(SecretPatternSetPort patternSetPort) {
    this.patternSetPort = Objects.requireNonNull(patternSetPort, "patternSetPort");
  }

  @Override
  public ResultRedaction redact(List<String> textContents, Map<String, Object> structuredContent) {
    Objects.requireNonNull(textContents, "textContents");
    Objects.requireNonNull(structuredContent, "structuredContent");
    List<SecretPattern> patterns = patternSetPort.activePatterns();
    List<String> sanitized = new ArrayList<>(textContents.size());
    List<SecretFinding> textFindings = new ArrayList<>();
    for (int index = 0; index < textContents.size(); index++) {
      RedactedText redacted =
          SecretRedactor.redact(textContents.get(index), patterns, "result.text[" + index + "]");
      sanitized.add(redacted.sanitizedText());
      textFindings.addAll(redacted.findings());
    }
    List<SecretFinding> structuredFindings =
        SecretScanner.scan(structuredContent, patterns, STRUCTURED_PREFIX).findings();
    return new ResultRedaction(sanitized, textFindings, structuredFindings);
  }
}
