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

import io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port.in.ApproveToolChangeUseCase;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port.out.ToolBaselineStorePort;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.ToolFingerprint;
import java.util.Objects;

/** Approving a change is replacing the baseline with the reviewed fingerprint — nothing more. */
public final class ApproveToolChangeService implements ApproveToolChangeUseCase {

  private final ToolBaselineStorePort store;

  public ApproveToolChangeService(ToolBaselineStorePort store) {
    this.store = Objects.requireNonNull(store, "store");
  }

  @Override
  public void approve(String toolName, ToolFingerprint approved) {
    Objects.requireNonNull(toolName, "toolName");
    Objects.requireNonNull(approved, "approved");
    if (toolName.isBlank()) {
      throw new IllegalArgumentException("toolName must not be blank");
    }
    store.replace(toolName, approved);
  }
}
