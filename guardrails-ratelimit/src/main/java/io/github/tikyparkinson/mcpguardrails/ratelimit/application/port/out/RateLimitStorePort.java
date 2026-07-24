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
package io.github.tikyparkinson.mcpguardrails.ratelimit.application.port.out;

import java.time.Instant;

/**
 * Outbound port for the rate limit counter store. Implementations must not swallow failures: a
 * broken store surfaces as a RuntimeException so the chain can fail closed.
 */
public interface RateLimitStorePort {

  /**
   * Atomically increments the counter for (agentId, toolName, windowStart) and returns the
   * resulting value (&gt;= 1, includes this invocation).
   */
  long incrementAndCount(String agentId, String toolName, Instant windowStart);
}
