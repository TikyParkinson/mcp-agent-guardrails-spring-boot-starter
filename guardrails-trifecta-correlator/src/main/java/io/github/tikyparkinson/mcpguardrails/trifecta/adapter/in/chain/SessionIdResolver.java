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
package io.github.tikyparkinson.mcpguardrails.trifecta.adapter.in.chain;

import io.github.tikyparkinson.mcpguardrails.core.adapter.in.mcp.GuardedToolCallHandler;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.SessionId;
import java.util.Objects;

/**
 * Decides what counts as a session for correlation purposes.
 *
 * <p>Replaceable because the answer depends on the deployment, the same reason {@code
 * AgentIdResolver} is replaceable in core.
 */
@FunctionalInterface
public interface SessionIdResolver {

  /** Derives the session this invocation belongs to. Never returns null. */
  SessionId resolve(ToolInvocationContext context);

  /**
   * Default resolution: the MCP transport session that {@code guardrails-core} leaves in the
   * invocation metadata, falling back to the agent when the transport carries none.
   *
   * <p>The two are not equivalent and the fallback is a degradation, not a variant. A transport
   * session identifies one connection; an agent identifier is the client product's name, shared by
   * everybody using it — so on the fallback path work by unrelated callers correlates together and
   * the trifecta can close across people who have nothing to do with each other. Callers can tell
   * which one they got from {@link #isFallback(SessionId)}.
   */
  static SessionIdResolver mcpSessionOrAgent() {
    return context -> {
      Objects.requireNonNull(context, "context");
      Object sessionId = context.metadata().get(GuardedToolCallHandler.SESSION_ID);
      if (sessionId instanceof String value && !value.isBlank()) {
        return SessionId.ofMcpSession(value);
      }
      return SessionId.ofAgent(context.agentId().value());
    };
  }

  /** True when the given session came from the agent rather than from a transport session. */
  static boolean isFallback(SessionId sessionId) {
    return Objects.requireNonNull(sessionId, "sessionId").value().startsWith("agent:");
  }
}
