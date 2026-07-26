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
package io.github.tikyparkinson.mcpguardrails.starter.infrastructure;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Notified of every tool definition as it is decorated, for guardrails that need to know what a
 * tool looked like at start-up rather than what an invocation claims.
 *
 * <p>Exists so {@link GuardrailToolSpecificationPostProcessor} does not have to depend on any
 * particular guardrail. Without an implementation registered, nothing is recorded and the
 * post-processor behaves exactly as before.
 *
 * <p>Registration happens at the moment a tool is decorated rather than after the context is ready:
 * an {@code ApplicationRunner} would leave a window in which the first invocation finds an empty
 * catalog and a guardrail decides on a definition it never saw.
 */
@FunctionalInterface
public interface ToolDefinitionRegistrar {

  /** Records the tool's public definition. Must not throw: a failure here would break start-up. */
  void register(McpSchema.Tool tool);

  /** Registrar that records nothing, used when no guardrail asked for the definitions. */
  static ToolDefinitionRegistrar noOp() {
    return tool -> {};
  }
}
