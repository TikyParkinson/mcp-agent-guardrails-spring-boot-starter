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
package io.github.tikyparkinson.mcpguardrails.toolintegrity.adapter.out.persistence;

import io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port.out.ToolBaselineStorePort;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.ToolFingerprint;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-memory baseline store. Note that TOFU baselines stored here do not survive a restart —
 * for real rug-pull protection across deployments use the JDBC adapter (or your own persistent
 * implementation of the port).
 */
public final class InMemoryToolBaselineStoreAdapter implements ToolBaselineStorePort {

  private final Map<String, ToolFingerprint> baselines = new ConcurrentHashMap<>();

  @Override
  public Optional<ToolFingerprint> find(String toolName) {
    Objects.requireNonNull(toolName, "toolName");
    return Optional.ofNullable(baselines.get(toolName));
  }

  @Override
  public ToolFingerprint establishIfAbsent(String toolName, ToolFingerprint candidate) {
    Objects.requireNonNull(toolName, "toolName");
    Objects.requireNonNull(candidate, "candidate");
    ToolFingerprint existing = baselines.putIfAbsent(toolName, candidate);
    return existing == null ? candidate : existing;
  }

  @Override
  public void replace(String toolName, ToolFingerprint fingerprint) {
    Objects.requireNonNull(toolName, "toolName");
    Objects.requireNonNull(fingerprint, "fingerprint");
    baselines.put(toolName, fingerprint);
  }
}
