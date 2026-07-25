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
package io.github.tikyparkinson.mcpguardrails.toolintegrity.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port.out.ToolBaselineStorePort;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.BaselineEstablished;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.Match;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.Mismatch;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.ToolDefinition;
import io.github.tikyparkinson.mcpguardrails.toolintegrity.domain.ToolFingerprint;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VerifyToolIntegrityServiceTest {

  private static final ToolDefinition DEFINITION =
      new ToolDefinition("search", "Search", "Searches docs", Map.of(), Map.of(), Map.of());
  private static final ToolFingerprint ACTUAL = ToolFingerprint.of(DEFINITION);
  private static final ToolFingerprint OTHER = new ToolFingerprint("a".repeat(64));

  private final ToolBaselineStorePort store = mock(ToolBaselineStorePort.class);
  private final VerifyToolIntegrityService service = new VerifyToolIntegrityService(store);

  @Test
  void shouldEstablishBaselineWhenToolSeenFirstTime() {
    // given: TOFU — no baseline yet, this fingerprint wins the establishment
    when(store.find("search")).thenReturn(Optional.empty());
    when(store.establishIfAbsent("search", ACTUAL)).thenReturn(ACTUAL);

    // when / then
    assertEquals(new BaselineEstablished(ACTUAL), service.verify(DEFINITION));
  }

  @Test
  void shouldMatchWhenDefinitionEqualsBaseline() {
    // given
    when(store.find("search")).thenReturn(Optional.of(ACTUAL));

    // when / then
    assertEquals(new Match(ACTUAL), service.verify(DEFINITION));
  }

  @Test
  void shouldMismatchWhenDefinitionDriftsFromBaseline() {
    // given: the stored baseline differs — poisoning signature
    when(store.find("search")).thenReturn(Optional.of(OTHER));

    // when / then
    assertEquals(new Mismatch(OTHER, ACTUAL), service.verify(DEFINITION));
  }

  @Test
  void shouldMismatchWhenEstablishmentRaceIsLostToDifferentFingerprint() {
    // given: between find() and establishIfAbsent() another instance stored a different baseline
    when(store.find("search")).thenReturn(Optional.empty());
    when(store.establishIfAbsent("search", ACTUAL)).thenReturn(OTHER);

    // when / then
    assertEquals(new Mismatch(OTHER, ACTUAL), service.verify(DEFINITION));
  }

  @Test
  void shouldPropagateStoreFailureWhenStoreThrows() {
    // given: fail-closed contract
    when(store.find(anyString())).thenThrow(new IllegalStateException("store down"));

    // when / then
    assertThrows(IllegalStateException.class, () -> service.verify(DEFINITION));
  }

  @Test
  void shouldRejectNullDefinitionWhenVerifying() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> service.verify(null));
    assertThrows(NullPointerException.class, () -> new VerifyToolIntegrityService(null));
  }

  @Test
  void shouldReplaceBaselineWhenChangeApproved() {
    // given
    ApproveToolChangeService approval = new ApproveToolChangeService(store);

    // when
    approval.approve("search", OTHER);

    // then: approving is exactly replacing the baseline with the reviewed fingerprint
    verify(store).replace("search", OTHER);
  }

  @Test
  void shouldRejectInvalidInputsWhenApproving() {
    // given
    ApproveToolChangeService approval = new ApproveToolChangeService(store);

    // when / then
    assertThrows(IllegalArgumentException.class, () -> approval.approve(" ", OTHER));
    assertThrows(NullPointerException.class, () -> approval.approve(null, OTHER));
    assertThrows(NullPointerException.class, () -> approval.approve("search", null));
    assertThrows(NullPointerException.class, () -> new ApproveToolChangeService(null));
  }
}
