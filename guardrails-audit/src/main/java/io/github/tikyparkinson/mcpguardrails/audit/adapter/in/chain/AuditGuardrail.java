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
package io.github.tikyparkinson.mcpguardrails.audit.adapter.in.chain;

import io.github.tikyparkinson.mcpguardrails.audit.application.port.in.RecordAuditEventUseCase;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEventType;
import io.github.tikyparkinson.mcpguardrails.audit.domain.NewAuditEvent;
import io.github.tikyparkinson.mcpguardrails.core.application.port.out.Guardrail;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import java.util.Objects;

/**
 * Observer guardrail: records a {@code TOOL_INVOKED} audit event for every tool invocation and
 * always allows. If the audit store fails, the exception propagates and the core chain converts it
 * into a fail-closed {@code Deny}.
 */
public final class AuditGuardrail implements Guardrail {

  public static final String NAME = "audit";

  private final RecordAuditEventUseCase recordAuditEvent;

  public AuditGuardrail(RecordAuditEventUseCase recordAuditEvent) {
    this.recordAuditEvent = Objects.requireNonNull(recordAuditEvent, "recordAuditEvent");
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public int order() {
    return -100;
  }

  @Override
  public GuardrailDecision evaluate(ToolInvocationContext context) {
    recordAuditEvent.record(
        new NewAuditEvent(
            context.agentId().value(),
            context.toolName().value(),
            NAME,
            AuditEventType.TOOL_INVOKED,
            ""));
    return new Allow();
  }
}
