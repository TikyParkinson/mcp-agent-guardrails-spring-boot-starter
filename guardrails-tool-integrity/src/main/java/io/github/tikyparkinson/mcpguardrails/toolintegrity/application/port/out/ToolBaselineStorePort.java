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
package io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port.out;

import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.ToolFingerprint;
import java.util.Optional;

/**
 * Outbound port for the trusted baseline store. Implementations must not swallow failures: a broken
 * store surfaces as a RuntimeException so the chain can fail closed.
 */
public interface ToolBaselineStorePort {

  /** Current baseline of the tool, if any. Never null. */
  Optional<ToolFingerprint> find(String toolName);

  /**
   * Atomically stores the candidate only when no baseline exists, and returns the baseline in force
   * after the operation (the candidate if it won, the pre-existing one otherwise).
   */
  ToolFingerprint establishIfAbsent(String toolName, ToolFingerprint candidate);

  /** Replaces (or creates) the baseline. Used by the approval flow. */
  void replace(String toolName, ToolFingerprint fingerprint);
}
