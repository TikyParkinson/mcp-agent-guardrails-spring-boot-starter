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
package io.github.tikyparkinson.mcpguardrails.toolintegrity.application.usecase;

import io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port.in.VerifyToolIntegrityUseCase;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port.out.ToolBaselineStorePort;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.BaselineEstablished;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.IntegrityCheckResult;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.Match;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.Mismatch;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.ToolDefinition;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.ToolFingerprint;
import java.util.Objects;
import java.util.Optional;

/**
 * TOFU verification: the first verified definition becomes the trusted baseline; later
 * verifications compare against it. Store failures propagate (fail-closed in the chain).
 */
public final class VerifyToolIntegrityService implements VerifyToolIntegrityUseCase {

  private final ToolBaselineStorePort store;

  public VerifyToolIntegrityService(ToolBaselineStorePort store) {
    this.store = Objects.requireNonNull(store, "store");
  }

  @Override
  public IntegrityCheckResult verify(ToolDefinition current) {
    Objects.requireNonNull(current, "current");
    ToolFingerprint actual = ToolFingerprint.of(current);
    Optional<ToolFingerprint> existing = store.find(current.toolName());
    if (existing.isPresent()) {
      return compare(existing.get(), actual);
    }
    ToolFingerprint winner = store.establishIfAbsent(current.toolName(), actual);
    return winner.equals(actual) ? new BaselineEstablished(actual) : new Mismatch(winner, actual);
  }

  private static IntegrityCheckResult compare(ToolFingerprint expected, ToolFingerprint actual) {
    return expected.equals(actual) ? new Match(actual) : new Mismatch(expected, actual);
  }
}
