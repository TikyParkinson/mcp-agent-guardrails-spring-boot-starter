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
package io.github.tikyparkinson.mcpguardrails.credentialleak.domain;

import java.util.Objects;

/**
 * One detection.
 *
 * <p>It never carries the detected value, not even truncated or partially masked: only which
 * pattern fired and where. A leak guard that wrote the secret into the reason of a {@code Deny} — a
 * message the model reads — would reintroduce the very leak it prevents.
 */
public record SecretFinding(String patternId, SecretSeverity severity, String location) {

  public SecretFinding {
    Objects.requireNonNull(patternId, "patternId");
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(location, "location");
    if (patternId.isBlank()) {
      throw new IllegalArgumentException("patternId must not be blank");
    }
    if (location.isBlank()) {
      throw new IllegalArgumentException("location must not be blank");
    }
  }

  /** Compact form used in decision reasons, e.g. {@code openai-api-key@arguments.token}. */
  public String describe() {
    return patternId + "@" + location;
  }
}
