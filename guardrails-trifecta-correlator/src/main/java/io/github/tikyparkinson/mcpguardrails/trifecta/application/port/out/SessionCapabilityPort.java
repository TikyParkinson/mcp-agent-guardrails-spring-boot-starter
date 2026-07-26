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
package io.github.tikyparkinson.mcpguardrails.trifecta.application.port.out;

import io.github.tikyparkinson.mcpguardrails.trifecta.domain.Capability;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.SessionAccumulation;
import io.github.tikyparkinson.mcpguardrails.trifecta.domain.SessionId;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Where sessions accumulate. Replace it to correlate across a fleet rather than one process.
 *
 * <p>The port is declared here, in this module's own vocabulary, rather than borrowed from another
 * guardrail: no guardrail depends on another (ARCHITECTURE.md §5), and a module that reasons across
 * invocations declares the question it needs answering (§5.2).
 */
public interface SessionCapabilityPort {

  /**
   * Adds the capabilities to the session and returns what it holds afterwards, this invocation
   * included, together with whether the three legs already met before it. Must be safe under
   * concurrency and atomic per session: two simultaneous invocations cannot lose either
   * contribution, and only one of them can report having closed the triangle.
   *
   * <p>Implementations decide expiry using the given instant rather than a clock of their own.
   */
  SessionAccumulation accumulate(
      SessionId sessionId, Set<Capability> capabilities, Instant occurredAt);

  /** Sessions holding all three legs, oldest first. Never null. */
  List<SessionId> withTrifecta();

  /** Forgets the session. False when it did not exist. */
  boolean forget(SessionId sessionId);
}
