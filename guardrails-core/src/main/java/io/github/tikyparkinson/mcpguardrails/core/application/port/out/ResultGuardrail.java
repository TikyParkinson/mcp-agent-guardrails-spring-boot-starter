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
package io.github.tikyparkinson.mcpguardrails.core.application.port.out;

import io.github.tikyparkinson.mcpguardrails.core.domain.ResultDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolResultContext;

/**
 * SPI implemented by feature modules that need to inspect what a tool <em>returns</em>, after it
 * has run and before the result reaches the agent (credential leak detection, output filtering).
 *
 * <p>Implementations must be side-effect safe with respect to the chain: rejections are expressed
 * as {@code Block}, never as thrown exceptions.
 */
public interface ResultGuardrail {

  /** Stable, unique name of this outbound guardrail (e.g. {@code "credential-leak"}). */
  String name();

  /** Evaluation order: lower runs earlier. Ties are broken by {@link #name()} ascending. */
  default int order() {
    return 0;
  }

  /** Inspects the tool result. Never returns null. */
  ResultDecision inspect(ToolResultContext context);
}
