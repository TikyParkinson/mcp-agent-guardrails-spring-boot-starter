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
 * Injection guardrail: scans tool arguments against the active detection rules. MALICIOUS denies,
 * SUSPICIOUS escalates.
 *
 * <p>It does not publish to the audit bus — ARCHITECTURE.md §5 forbids depending on another
 * guardrail module, and auditing happens once for the whole chain in {@code spring-boot-starter}.
 * The decision reason carries {@code ruleId@path} references and never the argument content itself
 * (PII rule), so what reaches the audit log stays free of the text that triggered the detection.
 */
public final class InjectionGuardrail implements Guardrail {

  public static final String GUARDRAIL_NAME = "injection-guard";

  private final ScanToolArgumentsUseCase scanArguments;

  public InjectionGuardrail(ScanToolArgumentsUseCase scanArguments) {
    this.scanArguments = Objects.requireNonNull(scanArguments, "scanArguments");
  }

  @Override
  public String name() {
    return GUARDRAIL_NAME;
  }

  @Override
  public int order() {
    return 50;
  }

  @Override
  public GuardrailDecision evaluate(ToolInvocationContext context) {
    ScanResult result = scanArguments.scan(context.arguments());
    if (result.clean()) {
      // A walk that ran out of budget did not clear the arguments, it stopped looking at them.
      return result.complete()
          ? new Allow()
          : new Deny("tool arguments too large to scan for injection");
    }
    InjectionSeverity severity = result.highestSeverity().orElseThrow();
    String detail = describeFindings(result);
    return switch (severity) {
      case MALICIOUS -> new Deny("malicious content detected in tool arguments (" + detail + ")");
      case SUSPICIOUS ->
          new Escalate("suspicious content detected in tool arguments (" + detail + ")");
    };
  }

  private static String describeFindings(ScanResult result) {
    return result.findings().stream()
        .map(finding -> finding.ruleId() + "@" + finding.argumentPath())
        .collect(Collectors.joining(", "));
  }
}
