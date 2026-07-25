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
package io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.in;

import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretFinding;
import java.util.List;
import java.util.Objects;

/**
 * Redaction outcome of one tool result: the sanitized texts plus the findings of each half.
 *
 * <p>The two finding lists are kept apart because they lead to different decisions: text findings
 * can be redacted, structured ones can only be blocked.
 */
public record ResultRedaction(
    List<String> sanitizedContents,
    List<SecretFinding> textFindings,
    List<SecretFinding> structuredFindings) {

  public ResultRedaction {
    sanitizedContents = List.copyOf(Objects.requireNonNull(sanitizedContents, "sanitizedContents"));
    textFindings = List.copyOf(Objects.requireNonNull(textFindings, "textFindings"));
    structuredFindings =
        List.copyOf(Objects.requireNonNull(structuredFindings, "structuredFindings"));
  }
}
