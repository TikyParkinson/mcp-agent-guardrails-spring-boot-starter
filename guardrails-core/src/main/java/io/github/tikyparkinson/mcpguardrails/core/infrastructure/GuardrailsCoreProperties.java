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
package io.github.tikyparkinson.mcpguardrails.core.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Global guardrails switch, bound to the {@code mcp.guardrails} prefix.
 *
 * @param enabled whether guardrails wrap MCP tool handlers at all. Default: {@code true}.
 */
@ConfigurationProperties(prefix = "mcp.guardrails")
public record GuardrailsCoreProperties(@DefaultValue("true") boolean enabled) {

  @ConstructorBinding
  public GuardrailsCoreProperties {}

  /** Default configuration: guardrails enabled. */
  public GuardrailsCoreProperties() {
    this(true);
  }
}
