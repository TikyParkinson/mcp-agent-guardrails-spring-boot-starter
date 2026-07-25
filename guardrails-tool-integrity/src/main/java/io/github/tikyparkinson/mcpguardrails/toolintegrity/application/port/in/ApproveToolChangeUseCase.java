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
package io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port.in;

import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.ToolFingerprint;

/**
 * Inbound port — the explicit approval flow. An exact fingerprint is approved (the one a {@code
 * Mismatch} reported), never "whatever the tool looks like now": what was reviewed is what gets
 * trusted.
 */
public interface ApproveToolChangeUseCase {

  /** Replaces the tool's baseline with the approved fingerprint. */
  void approve(String toolName, ToolFingerprint approved);
}
