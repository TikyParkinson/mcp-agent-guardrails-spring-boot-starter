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
package io.github.tikyparkinson.mcpguardrails.credentialleak.application.usecase;

import io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.in.ScanToolArgumentsForSecretsUseCase;
import io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.out.SecretPatternSetPort;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretScanResult;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretScanner;
import java.util.Map;
import java.util.Objects;

/** Scans the arguments of an invocation for credentials, reporting where each one was found. */
public final class ScanToolArgumentsForSecretsService
    implements ScanToolArgumentsForSecretsUseCase {

  /**
   * Name of the scanned parameter, which is also the prefix of every reported location: a finding
   * at {@code arguments.token} is a finding inside this parameter.
   */
  private static final String ARGUMENTS = "arguments";

  private final SecretPatternSetPort patternSetPort;

  public ScanToolArgumentsForSecretsService(SecretPatternSetPort patternSetPort) {
    this.patternSetPort = Objects.requireNonNull(patternSetPort, "patternSetPort");
  }

  @Override
  public SecretScanResult scan(Map<String, Object> arguments) {
    Objects.requireNonNull(arguments, ARGUMENTS);
    return SecretScanner.scan(arguments, patternSetPort.activePatterns(), ARGUMENTS);
  }
}
