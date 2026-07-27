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
package io.github.tikyparkinson.mcpguardrails.trifecta.infrastructure;

import io.github.tikyparkinson.mcpguardrails.trifecta.domain.TrifectaPolicy;
import java.util.List;
import java.util.Objects;

/**
 * What this guardrail cannot do with the configuration it was given, in a form the wiring layer can
 * log at start-up.
 *
 * <p>A guardrail that is registered but inert is indistinguishable from one that is working, and an
 * operator who believes they have a protection they do not have is worse off than one who knows
 * they have none. ARCHITECTURE.md §5.2 makes announcing this a requirement rather than a courtesy.
 */
public final class TrifectaStartupWarnings {

  private TrifectaStartupWarnings() {}

  /**
   * Warnings for the given policy, empty when the guardrail is fully operational. The wiring layer
   * decides how to surface them; this class only decides what is worth saying.
   */
  public static List<String> of(TrifectaPolicy policy) {
    Objects.requireNonNull(policy, "policy");
    if (policy.declaresNothing()) {
      return List.of(
          "trifecta-correlator is registered but no tool declares any capability under"
              + " mcp.guardrails.trifecta.tools, so it will never detect anything."
              + " Declare which tools touch private data, untrusted content or external"
              + " communication, or disable the guardrail.");
    }
    return List.of();
  }

  /**
   * Warning for a session derived from the agent rather than from a transport session.
   *
   * <p>Separate from {@link #of(TrifectaPolicy)} because it is not known from configuration: it
   * depends on what the transport supplies at runtime, so the wiring layer emits it the first time
   * it sees the fallback.
   */
  public static String agentFallbackWarning() {
    return "trifecta-correlator is correlating by agent because the MCP transport carries no"
        + " session. The agent identifier is the client product's name and is shared by every"
        + " caller using it, so unrelated work will correlate together and the trifecta may close"
        + " across different people. Publish a SessionIdResolver bean if your deployment can"
        + " identify a conversation.";
  }
}
