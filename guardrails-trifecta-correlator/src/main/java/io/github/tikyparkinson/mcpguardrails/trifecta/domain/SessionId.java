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
package io.github.tikyparkinson.mcpguardrails.trifecta.domain;

import java.util.Objects;

/**
 * The session the correlation runs over.
 *
 * <p>A type of its own rather than a bare string because what counts as a session depends on the
 * deployment, and the two ways of deriving one are not equivalent: an MCP transport session
 * identifies a single connection, while an agent identifier is the client product's name and is
 * shared by everyone using it.
 */
public record SessionId(String value) {

  public SessionId {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("SessionId value must not be blank");
    }
  }

  /** From the MCP transport session: one connection, one session. */
  public static SessionId ofMcpSession(String sessionId) {
    return new SessionId("mcp:" + Objects.requireNonNull(sessionId, "sessionId"));
  }

  /**
   * From the agent alone, for transports that carry no session. Coarser: every caller using the
   * same client shares it, so unrelated work correlates together.
   */
  public static SessionId ofAgent(String agentId) {
    return new SessionId("agent:" + Objects.requireNonNull(agentId, "agentId"));
  }
}
