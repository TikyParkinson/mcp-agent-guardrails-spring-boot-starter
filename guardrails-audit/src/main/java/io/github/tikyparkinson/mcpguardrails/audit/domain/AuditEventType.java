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
package io.github.tikyparkinson.mcpguardrails.audit.domain;

/** Closed set of audit event types. */
public enum AuditEventType {

  /** A tool call reached the chain. Emitted before any guardrail has decided. */
  TOOL_INVOKED,

  /** A guardrail permitted the invocation. */
  DECISION_ALLOW,

  /** A guardrail blocked the invocation. */
  DECISION_DENY,

  /** A guardrail asked for a human decision. */
  DECISION_ESCALATE,

  /** The outbound chain returned the tool result untouched. */
  RESULT_PASS_THROUGH,

  /** The outbound chain removed something from the tool result before returning it. */
  RESULT_REDACTED,

  /** The outbound chain stopped the tool result from reaching the agent. */
  RESULT_BLOCKED,

  /**
   * An escalation ended. The detail says how: approved or rejected by a person, or expired because
   * nobody answered — a denial nobody decided, which is not the same thing.
   */
  APPROVAL_RESOLVED
}
