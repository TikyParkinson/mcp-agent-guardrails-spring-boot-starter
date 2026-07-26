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
package io.github.tikyparkinson.mcpguardrails.anomaly.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.anomaly.application.port.out.InvocationHistoryPort;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.AgentHistory;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.AnomalyDetected;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.AnomalyPolicy;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.ArgumentsFingerprint;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.InvocationRecord;
import io.github.tikyparkinson.mcpguardrails.anomaly.domain.NoAnomaly;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class DetectAnomalyServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");
  private static final AnomalyPolicy POLICY = new AnomalyPolicy(Duration.ofMinutes(1), 5, 3, 20L);
  private static final Map<String, Object> ARGUMENTS = Map.of("q", "x");

  private InvocationHistoryPort historyPort;
  private DetectAnomalyService service;

  @BeforeEach
  void setUp() {
    historyPort = mock(InvocationHistoryPort.class);
    when(historyPort.historyOf(any(), any())).thenReturn(new AgentHistory(List.of(), Set.of(), 0L));
    service = new DetectAnomalyService(historyPort, POLICY);
  }

  @Test
  void shouldRecordTheInvocationBeforeReadingTheHistory() {
    // given an agent making a call
    // when inspected
    service.inspect("agent", "search", ARGUMENTS, NOW);

    // then the call is stored first and only then analysed, so the invocation that reaches the
    // threshold is the one that gets stopped rather than the one after it
    InOrder order = inOrder(historyPort);
    order.verify(historyPort).record(any());
    order.verify(historyPort).historyOf(eq("agent"), any());
  }

  @Test
  void shouldReadTheHistoryFromOneWindowBeforeTheInvocation() {
    // given a one minute window
    // when inspected
    service.inspect("agent", "search", ARGUMENTS, NOW);

    // then the window starts one minute before the invocation instant, not before "now": the
    // analysis must not depend on how long the guardrails ahead of it took
    verify(historyPort).historyOf("agent", NOW.minusSeconds(60));
  }

  @Test
  void shouldRecordTheFingerprintOfTheArgumentsAndNotTheArguments() {
    // given arguments carrying a secret
    // when inspected
    service.inspect("agent", "login", Map.of("password", "hunter2"), NOW);

    // then what reaches the history is a digest
    ArgumentCaptor<InvocationRecord> captor = ArgumentCaptor.forClass(InvocationRecord.class);
    verify(historyPort).record(captor.capture());
    assertEquals(
        ArgumentsFingerprint.of(Map.of("password", "hunter2")), captor.getValue().fingerprint());
  }

  @Test
  void shouldReportNoAnomalyWhenTheHistoryIsClean() {
    // given an agent with no history
    // when inspected
    // then nothing is reported
    assertInstanceOf(NoAnomaly.class, service.inspect("agent", "search", ARGUMENTS, NOW));
  }

  @Test
  void shouldReportAnAnomalyWhenTheHistorySaysSo() {
    // given a history already holding five identical calls
    List<InvocationRecord> records = new ArrayList<>();
    for (int index = 0; index < 5; index++) {
      records.add(new InvocationRecord("agent", "search", ArgumentsFingerprint.of(ARGUMENTS), NOW));
    }
    when(historyPort.historyOf(any(), any()))
        .thenReturn(new AgentHistory(records, Set.of("search"), 50L));

    // when inspected
    // then the verdict carries the loop
    AnomalyDetected detected =
        assertInstanceOf(AnomalyDetected.class, service.inspect("agent", "search", ARGUMENTS, NOW));
    assertEquals(1, detected.signals().size());
  }

  @Test
  void shouldRejectAnInspectionWithoutAnAgent() {
    // given no agent
    // when inspected
    // then it fails
    assertThrows(NullPointerException.class, () -> service.inspect(null, "search", ARGUMENTS, NOW));
  }

  @Test
  void shouldRejectAnInspectionWithoutArguments() {
    // given no arguments
    // when inspected
    // then it fails rather than fingerprinting an empty map, which would make unrelated calls
    // look identical
    assertThrows(NullPointerException.class, () -> service.inspect("agent", "search", null, NOW));
  }

  @Test
  void shouldRejectAnInspectionWithoutAnInstant() {
    // given no instant
    // when inspected
    // then it fails
    assertThrows(
        NullPointerException.class, () -> service.inspect("agent", "search", ARGUMENTS, null));
  }

  @Test
  void shouldRejectAnInspectionWithoutAToolName() {
    // given no tool name
    // when inspected
    // then it fails
    assertThrows(NullPointerException.class, () -> service.inspect("agent", null, ARGUMENTS, NOW));
  }

  @Test
  void shouldRejectAServiceWithoutAHistoryPort() {
    // given no port
    // when the service is built
    // then it fails at wiring time rather than on the first invocation
    assertThrows(NullPointerException.class, () -> new DetectAnomalyService(null, POLICY));
  }

  @Test
  void shouldRejectAServiceWithoutAPolicy() {
    // given no policy
    // when the service is built
    // then it fails
    assertThrows(NullPointerException.class, () -> new DetectAnomalyService(historyPort, null));
  }
}
