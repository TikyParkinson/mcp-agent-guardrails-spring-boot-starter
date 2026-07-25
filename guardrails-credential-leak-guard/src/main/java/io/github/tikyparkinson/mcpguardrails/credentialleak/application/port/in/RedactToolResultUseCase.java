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

import java.util.List;
import java.util.Map;

/** Inbound port used by the outbound guardrail to sanitize the result of an invocation. */
public interface RedactToolResultUseCase {

  /**
   * Redacts the textual contents of the result and scans — without redacting — its structured
   * content. Never returns null, and {@code sanitizedContents} always has the same size as {@code
   * textContents}, as the {@code Redact} contract of core requires.
   */
  ResultRedaction redact(List<String> textContents, Map<String, Object> structuredContent);
}
