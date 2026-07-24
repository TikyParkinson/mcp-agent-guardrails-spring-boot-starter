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
package io.github.tikyparkinson.mcpguardrails.injectionguard.adapter.in.chain;

import io.github.tikyparkinson.mcpguardrails.audit.application.port.in.RecordAuditEventUseCase;
import io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEventType;
import io.github.tikyparkinson.mcpguardrails.audit.domain.NewAuditEvent;
import io.github.tikyparkinson.mcpguardrails.core.application.port.out.Guardrail;
import io.github.tikyparkinson.mcpguardrails.core.domain.Allow;
import io.github.tikyparkinson.mcpguardrails.core.domain.Deny;
import io.github.tikyparkinson.mcpguardrails.core.domain.Escalate;
import io.github.tikyparkinson.mcpguardrails.core.domain.GuardrailDecision;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;
import io.github.tikyparkinson.mcpguardrails.injectionguard.application.port.in.ScanToolArgumentsUseCase;
import io.github.tikyparkinson.mcpguardrails.injectionguard.domain.InjectionSeverity;
import io.github.tikyparkinson.mcpguardrails.injectionguard.domain.ScanResult;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Injection guardrail: scans tool arguments against the active detection rules. Clean scans pass
 * silently ({@code TOOL_INVOKED} is already recorded by the audit guardrail); detections are
 * recorded on the audit bus with {@code ruleId@path} references — never the argument content itself
 * (PII rule). MALICIOUS denies, SUSPICIOUS escalates.
 */
public final class InjectionGuardrail implements Guardrail {

  public static final String NAME = "injection-guard";

  private final ScanToolArgumentsUseCase scanArguments;
  private final RecordAuditEventUseCase auditBus;

  public InjectionGuardrail(
      ScanToolArgumentsUseCase scanArguments, RecordAuditEventUseCase auditBus) {
    this.scanArguments = Objects.requireNonNull(scanArguments, "scanArguments");
    this.auditBus = Objects.requireNonNull(auditBus, "auditBus");
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public int order() {
    return 50;
  }

  @Override
  public GuardrailDecision evaluate(ToolInvocationContext context) {
    ScanResult result = scanArguments.scan(context.arguments());
    if (result.clean()) {
      return new Allow();
    }
    InjectionSeverity severity = result.highestSeverity().orElseThrow();
    String detail = describeFindings(result);
    recordDetection(context, severity, detail);
    return switch (severity) {
      case MALICIOUS -> new Deny("malicious content detected in tool arguments (" + detail + ")");
      case SUSPICIOUS ->
          new Escalate("suspicious content detected in tool arguments (" + detail + ")");
    };
  }

  private void recordDetection(
      ToolInvocationContext context, InjectionSeverity severity, String detail) {
    AuditEventType type =
        severity == InjectionSeverity.MALICIOUS
            ? AuditEventType.DECISION_DENY
            : AuditEventType.DECISION_ESCALATE;
    auditBus.record(
        new NewAuditEvent(
            context.agentId().value(), context.toolName().value(), NAME, type, detail));
  }

  private static String describeFindings(ScanResult result) {
    return result.findings().stream()
        .map(finding -> finding.ruleId() + "@" + finding.argumentPath())
        .collect(Collectors.joining(", "));
  }
}
