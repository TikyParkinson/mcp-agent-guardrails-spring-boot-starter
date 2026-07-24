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
package io.github.tikyparkinson.mcpguardrails.injectionguard.application.usecase;

import io.github.tikyparkinson.mcpguardrails.injectionguard.application.port.in.ScanToolArgumentsUseCase;
import io.github.tikyparkinson.mcpguardrails.injectionguard.application.port.out.InjectionRuleSetPort;
import io.github.tikyparkinson.mcpguardrails.injectionguard.domain.ArgumentScanner;
import io.github.tikyparkinson.mcpguardrails.injectionguard.domain.ScanResult;
import java.util.Map;
import java.util.Objects;

/** Fetches the active rules and lets the domain scanner do the work. */
public final class ScanToolArgumentsService implements ScanToolArgumentsUseCase {

  private final InjectionRuleSetPort ruleSetPort;

  public ScanToolArgumentsService(InjectionRuleSetPort ruleSetPort) {
    this.ruleSetPort = Objects.requireNonNull(ruleSetPort, "ruleSetPort");
  }

  @Override
  public ScanResult scan(Map<String, Object> arguments) {
    Objects.requireNonNull(arguments, "arguments");
    return ArgumentScanner.scan(arguments, ruleSetPort.activeRules());
  }
}
