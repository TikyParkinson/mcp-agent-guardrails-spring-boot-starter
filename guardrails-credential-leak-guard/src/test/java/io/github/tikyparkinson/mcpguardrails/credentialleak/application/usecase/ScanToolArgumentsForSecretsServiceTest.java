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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.out.SecretPatternSetPort;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretFinding;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretPattern;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretScanResult;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretSeverity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScanToolArgumentsForSecretsServiceTest {

  private static final SecretPattern TOKEN =
      SecretPattern.of("openai", "sk-[A-Za-z0-9]{10,}", SecretSeverity.CONFIRMED);

  private final SecretPatternSetPort patternSetPort = mock(SecretPatternSetPort.class);
  private final ScanToolArgumentsForSecretsService service =
      new ScanToolArgumentsForSecretsService(patternSetPort);

  @Test
  void shouldReportNothingWhenArgumentsAreClean() {
    // given
    when(patternSetPort.activePatterns()).thenReturn(List.of(TOKEN));

    // when
    SecretScanResult result = service.scan(Map.of("query", "weather in Madrid"));

    // then
    assertTrue(result.clean());
  }

  @Test
  void shouldReportArgumentsLocationWhenCredentialIsFound() {
    // given
    when(patternSetPort.activePatterns()).thenReturn(List.of(TOKEN));

    // when
    SecretScanResult result = service.scan(Map.of("token", "sk-abcdefghij123"));

    // then
    assertEquals(
        List.of("openai@arguments.token"),
        result.findings().stream().map(SecretFinding::describe).toList());
  }

  @Test
  void shouldUseTheCurrentPatternsWhenTheSetChanges() {
    // given: patterns are read on every scan so they can rotate without a restart
    when(patternSetPort.activePatterns()).thenReturn(List.of(), List.of(TOKEN));
    Map<String, Object> arguments = Map.of("token", "sk-abcdefghij123");

    // when
    SecretScanResult first = service.scan(arguments);
    SecretScanResult second = service.scan(arguments);

    // then
    assertTrue(first.clean());
    assertEquals(1, second.findings().size());
  }

  @Test
  void shouldRejectNullArgumentsWhenScanning() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> service.scan(null));
  }

  @Test
  void shouldRejectNullPortWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new ScanToolArgumentsForSecretsService(null));
  }
}
