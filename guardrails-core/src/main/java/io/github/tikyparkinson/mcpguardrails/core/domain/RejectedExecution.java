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
package io.github.tikyparkinson.mcpguardrails.core.domain;

import java.util.Objects;

/**
 * Nobody authorized the escalated invocation: an explicit refusal, an expired deadline, or a
 * resolver that could not be asked at all.
 *
 * <p>The three converge here on purpose. To the handler they are one fact — this must not run — and
 * separating them into types would force every caller to branch on a distinction that does not
 * change what it does. What does differ between them is what to tell the operator, and that travels
 * in the reason.
 *
 * @param reason human-readable motive, forwarded to the agent
 */
public record RejectedExecution(String reason) implements EscalationOutcome {

  public RejectedExecution {
    Objects.requireNonNull(reason, "reason");
    if (reason.isBlank()) {
      throw new IllegalArgumentException("RejectedExecution reason must not be blank");
    }
  }
}
