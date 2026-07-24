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
package io.github.tikyparkinson.mcpguardrails.core.adapter.in.mcp;

import io.github.tikyparkinson.mcpguardrails.core.domain.AgentId;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;

/** Resolves the identity of the calling agent from the MCP server exchange. */
@FunctionalInterface
public interface AgentIdResolver {

  AgentId resolve(McpSyncServerExchange exchange);

  /**
   * Default resolution: the MCP client implementation name, or {@code "unknown"} when the exchange
   * carries no client info.
   */
  static AgentIdResolver clientInfoName() {
    return exchange -> {
      if (exchange == null) {
        return new AgentId("unknown");
      }
      McpSchema.Implementation clientInfo = exchange.getClientInfo();
      if (clientInfo == null || clientInfo.name() == null || clientInfo.name().isBlank()) {
        return new AgentId("unknown");
      }
      return new AgentId(clientInfo.name());
    };
  }
}
