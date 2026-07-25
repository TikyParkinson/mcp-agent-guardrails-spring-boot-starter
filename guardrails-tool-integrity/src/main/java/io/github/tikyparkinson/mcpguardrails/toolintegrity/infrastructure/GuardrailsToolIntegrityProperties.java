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
package io.github.tikyparkinson.mcpguardrails.toolintegrity.infrastructure;

import io.github.tikyparkinson.mcpguardrails.toolintegrity.adapter.in.chain.MismatchAction;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.adapter.in.chain.UnknownDefinitionAction;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tool-integrity configuration, bound to the {@code mcp.guardrails.tool-integrity} prefix.
 *
 * @param enabled whether the tool-integrity guardrail is registered. Default: {@code true}.
 * @param onMismatch action when a definition drifts from its baseline. Default: {@code DENY} — an
 *     unapproved definition change is an attack signature, not a policy ambiguity.
 * @param onUnknownDefinition action when the invoked tool has no registered definition. Default:
 *     {@code ALLOW} — tools outside the decorated set cannot be fingerprinted; harden to DENY if
 *     every tool is expected to be registered.
 */
@ConfigurationProperties(prefix = "mcp.guardrails.tool-integrity")
public record GuardrailsToolIntegrityProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("DENY") MismatchAction onMismatch,
    @DefaultValue("ALLOW") UnknownDefinitionAction onUnknownDefinition) {

  @ConstructorBinding
  public GuardrailsToolIntegrityProperties {}

  /** Default configuration: enabled, deny on mismatch, allow unknown definitions. */
  public GuardrailsToolIntegrityProperties() {
    this(true, MismatchAction.DENY, UnknownDefinitionAction.ALLOW);
  }
}
