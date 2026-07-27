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

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * What a session has accumulated so far.
 *
 * <p>Capabilities are never taken away. That is what makes a closed trifecta stay closed for the
 * rest of the session without a separate flag to keep in sync: any later invocation sees the three
 * again, even one to a harmless tool.
 *
 * @param capabilities legs seen in this session
 * @param startedAt first invocation, which bounds the session's absolute age
 * @param lastSeenAt latest invocation, which bounds its idleness
 */
public record SessionCapabilities(
    Set<Capability> capabilities, Instant startedAt, Instant lastSeenAt) {

  public SessionCapabilities {
    capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
    Objects.requireNonNull(startedAt, "startedAt");
    Objects.requireNonNull(lastSeenAt, "lastSeenAt");
    if (lastSeenAt.isBefore(startedAt)) {
      throw new IllegalArgumentException(
          "lastSeenAt (%s) must not precede startedAt (%s)".formatted(lastSeenAt, startedAt));
    }
  }

  /** A session that has just seen its first invocation. */
  public static SessionCapabilities starting(Set<Capability> capabilities, Instant at) {
    return new SessionCapabilities(capabilities, at, at);
  }

  /** Adds what an invocation contributes, keeping the original start. */
  public SessionCapabilities plus(Set<Capability> added, Instant at) {
    Objects.requireNonNull(added, "added");
    Set<Capability> merged = EnumSet.noneOf(Capability.class);
    merged.addAll(capabilities);
    merged.addAll(added);
    return new SessionCapabilities(merged, startedAt, at);
  }

  /** True when all three legs are present. */
  public boolean hasTrifecta() {
    return capabilities.size() == Capability.values().length;
  }

  /** The legs still missing, so a reason can say how close the session is. */
  public Set<Capability> missing() {
    Set<Capability> absent = EnumSet.allOf(Capability.class);
    absent.removeAll(capabilities);
    return Set.copyOf(absent);
  }
}
