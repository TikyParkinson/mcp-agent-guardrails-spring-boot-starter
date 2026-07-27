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
package io.github.tikyparkinson.mcpguardrails.credentialleak.infrastructure;

import io.github.tikyparkinson.mcpguardrails.credentialleak.adapter.in.chain.InputAction;
import io.github.tikyparkinson.mcpguardrails.credentialleak.adapter.in.chain.OutputAction;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.BuiltInSecretPatterns;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.ScanBudget;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretPattern;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretSeverity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Credential-leak configuration, bound to the {@code mcp.guardrails.credential-leak} prefix.
 *
 * @param enabled whether both guardrails (inbound and outbound) are registered. Default: {@code
 *     true}.
 * @param builtInPatternsEnabled whether the eleven built-in patterns are included. Default: {@code
 *     true}.
 * @param onConfirmedInput action when an unmistakable credential reaches the arguments. Default:
 *     {@code DENY} — the call is stopped, arguments are never rewritten.
 * @param onSuspectedInput action when only a keyword heuristic matched the arguments. Default:
 *     {@code ESCALATE} — a false positive should not silently kill a legitimate call.
 * @param onOutputText action when a credential appears in the textual contents of a result.
 *     Default: {@code REDACT} — the rest of the answer is still useful to the model. A finding in
 *     the structured content is always blocked and has no property: it cannot be rewritten.
 * @param customPatterns additional patterns appended after the built-in ones. Default: empty.
 * @param maxScanNodes values examined before the scan gives up. Default: {@code 10000}. Reaching it
 *     denies the call rather than allowing it: arguments nobody finished looking at are not
 *     arguments known to be clean. The worst case it admits costs about 3 ms.
 * @param maxScanDepth nesting levels explored before the scan gives up. Default: {@code 64}. A
 *     guard against runaway recursion, not a cost control — depth is cheap, node count is not.
 */
@ConfigurationProperties(prefix = "mcp.guardrails.credential-leak")
public record GuardrailsCredentialLeakProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("true") boolean builtInPatternsEnabled,
    @DefaultValue("DENY") InputAction onConfirmedInput,
    @DefaultValue("ESCALATE") InputAction onSuspectedInput,
    @DefaultValue("REDACT") OutputAction onOutputText,
    List<CustomPattern> customPatterns,
    @DefaultValue("10000") int maxScanNodes,
    @DefaultValue("64") int maxScanDepth) {

  @ConstructorBinding
  public GuardrailsCredentialLeakProperties {
    customPatterns = customPatterns == null ? List.of() : List.copyOf(customPatterns);
  }

  /** Default configuration: enabled, built-in patterns on, deny confirmed, redact on output. */
  public GuardrailsCredentialLeakProperties() {
    this(true, true, InputAction.DENY, InputAction.ESCALATE, OutputAction.REDACT, List.of());
  }

  /**
   * The form this record had before the scan budget existed, kept so callers that predate it still
   * compile. Uses the default budget.
   */
  public GuardrailsCredentialLeakProperties(
      boolean enabled,
      boolean builtInPatternsEnabled,
      InputAction onConfirmedInput,
      InputAction onSuspectedInput,
      OutputAction onOutputText,
      List<CustomPattern> customPatterns) {
    this(
        enabled,
        builtInPatternsEnabled,
        onConfirmedInput,
        onSuspectedInput,
        onOutputText,
        customPatterns,
        ScanBudget.defaults().maxNodes(),
        ScanBudget.defaults().maxDepth());
  }

  /** The scan budget this configuration describes. */
  public ScanBudget toBudget() {
    return new ScanBudget(maxScanNodes, maxScanDepth);
  }

  /** Builds the pattern list this configuration describes (built-ins first, then customs). */
  public List<SecretPattern> toPatterns() {
    List<SecretPattern> patterns = new ArrayList<>();
    if (builtInPatternsEnabled) {
      patterns.addAll(BuiltInSecretPatterns.defaults());
    }
    for (CustomPattern custom : customPatterns) {
      patterns.add(
          SecretPattern.of(custom.id(), custom.regex(), custom.severity(), custom.secretGroup()));
    }
    return List.copyOf(patterns);
  }

  /**
   * One configured custom pattern.
   *
   * @param id stable pattern id, reported in decisions and redaction markers.
   * @param regex compiled case-insensitive.
   * @param severity CONFIRMED for unmistakable formats, SUSPECTED for heuristics.
   * @param secretGroup capture group holding the value; {@code 0} (default) redacts the whole
   *     match. Use a group when the regex also matches the key in front of the value.
   */
  public record CustomPattern(
      String id, String regex, SecretSeverity severity, @DefaultValue("0") int secretGroup) {}
}
