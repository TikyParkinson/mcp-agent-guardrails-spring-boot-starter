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
package io.github.tikyparkinson.mcpguardrails.approval.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * The invocation is not authorized: an explicit refusal, an expired deadline, or a channel with no
 * room to ask.
 *
 * <p>All three land here on purpose. Every path that is not an explicit approval must end in a
 * rejection, and giving each its own type would invite treating "it expired" as something milder
 * than "they said no". What differs between them is what to tell the operator, which is what the
 * reason carries.
 *
 * @param approver who rejected, or {@link #SYSTEM} when no person was involved
 * @param reason human-readable motive
 */
public record Rejected(String approver, String reason) implements ApprovalDecision {

  /** Stands in for the approver when the rejection was automatic. */
  public static final String SYSTEM = "system";

  public Rejected {
    requireNotBlank(approver, "approver");
    requireNotBlank(reason, "reason");
  }

  /** Nobody answered within the deadline. */
  public static Rejected byTimeout(Duration timeout) {
    Objects.requireNonNull(timeout, "timeout");
    return new Rejected(SYSTEM, "no approval within " + timeout);
  }

  /** The channel was already holding as many requests as it admits. */
  public static Rejected byQuota(int limit) {
    return new Rejected(SYSTEM, "too many approvals pending (limit " + limit + ")");
  }

  private static void requireNotBlank(String value, String field) {
    Objects.requireNonNull(value, field);
    if (value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
