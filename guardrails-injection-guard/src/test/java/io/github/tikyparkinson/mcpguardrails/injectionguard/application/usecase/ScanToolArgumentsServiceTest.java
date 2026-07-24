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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.injectionguard.application.port.out.InjectionRuleSetPort;
import io.github.tikyparkinson.mcpguardrails.injectionguard.domain.InjectionRule;
import io.github.tikyparkinson.mcpguardrails.injectionguard.domain.InjectionSeverity;
import io.github.tikyparkinson.mcpguardrails.injectionguard.domain.ScanResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScanToolArgumentsServiceTest {

  private final InjectionRuleSetPort ruleSetPort = mock(InjectionRuleSetPort.class);
  private final ScanToolArgumentsService service = new ScanToolArgumentsService(ruleSetPort);

  @Test
  void shouldReportFindingWhenActiveRuleMatchesArgument() {
    // given
    when(ruleSetPort.activeRules())
        .thenReturn(List.of(InjectionRule.of("evil", "evil", InjectionSeverity.MALICIOUS)));

    // when
    ScanResult result = service.scan(Map.of("q", "some evil text"));

    // then
    assertEquals(
        List.of(new ScanResult.Finding("evil", InjectionSeverity.MALICIOUS, "q")),
        result.findings());
  }

  @Test
  void shouldReturnCleanWhenNoRulesActive() {
    // given
    when(ruleSetPort.activeRules()).thenReturn(List.of());

    // when / then
    assertTrue(service.scan(Map.of("q", "anything")).clean());
  }

  @Test
  void shouldRejectNullArgumentsWhenScanning() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> service.scan(null));
  }

  @Test
  void shouldRejectNullPortWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new ScanToolArgumentsService(null));
  }
}
