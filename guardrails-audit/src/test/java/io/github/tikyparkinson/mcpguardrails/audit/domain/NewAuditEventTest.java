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
package io.github.tikyparkinson.mcpguardrails.audit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * An agent chooses the names of its own arguments and, with the default resolver, its own
 * identifier. Both reach the trail inside decision reasons, so both have to arrive harmless.
 */
class NewAuditEventTest {

  @Test
  void shouldStripNewlinesFromTheDetailWhenBuilt() {
    // given a detail carrying a forged entry, as an argument name could
    NewAuditEvent event =
        new NewAuditEvent(
            "attacker",
            "search",
            "injection-guard",
            AuditEventType.DECISION_DENY,
            "malicious content (rule@q\nattacker audit TOOL_INVOKED forged)");

    // when it is read back
    // then it is a single line. Otherwise an agent could write a whole fake audit record once the
    // trail is dumped to a plain-text log
    assertFalse(event.detail().contains("\n"));
  }

  @Test
  void shouldStripNewlinesFromTheAgentIdWhenBuilt() {
    // given an agent that named itself with a newline in it
    NewAuditEvent event =
        new NewAuditEvent(
            "attacker\nfake-agent audit TOOL_INVOKED forged",
            "search",
            "authz",
            AuditEventType.DECISION_ALLOW,
            "default");

    // when it is read back
    // then the identifier is one line too
    assertFalse(event.agentId().contains("\n"));
  }

  @Test
  void shouldStripBidirectionalOverridesWhenBuilt() {
    // given a detail with a right-to-left override, which reverses how the rest reads
    NewAuditEvent event =
        new NewAuditEvent(
            "agent-1",
            "search",
            "authz",
            AuditEventType.DECISION_ALLOW,
            "allowed\u202Egnitirw sdrawkcab");

    // when it is read back
    // then the override is gone. It leaves no newline behind, but it makes a reviewer read a line
    // as something other than what was stored
    assertFalse(event.detail().contains("\u202E"));
  }

  @Test
  void shouldReplaceTabsAndCarriageReturnsWithSpacesWhenBuilt() {
    // given a detail that would break a column-separated log
    NewAuditEvent event =
        new NewAuditEvent(
            "agent-1", "search", "authz", AuditEventType.DECISION_ALLOW, "col1\tcol2\r\nrow");

    // when it is read back
    // then every unprintable character became a space, keeping the text readable
    assertEquals("col1 col2  row", event.detail());
  }

  @Test
  void shouldKeepAnOrdinaryValueUntouchedWhenBuilt() {
    // given the normal case
    NewAuditEvent event =
        new NewAuditEvent("agent-1", "search", "authz", AuditEventType.DECISION_ALLOW, "rule[0]");

    // when it is read back
    // then nothing changed: sanitizing must not disturb the events that make up the whole trail
    assertEquals("rule[0]", event.detail());
    assertEquals("agent-1", event.agentId());
  }

  @Test
  void shouldAcceptAnEmptyDetailWhenBuilt() {
    // given an event with nothing to add, like TOOL_INVOKED
    NewAuditEvent event =
        new NewAuditEvent("agent-1", "search", "audit", AuditEventType.TOOL_INVOKED, "");

    // when it is read back
    // then the empty detail is legitimate — only the identifying fields must not be blank
    assertEquals("", event.detail());
  }

  @Test
  void shouldRejectAnAgentIdMadeOnlyOfControlCharactersWhenBuilt() {
    // given an identifier that is blank once sanitized
    // when / then it is rejected: an event that cannot say who caused it is not worth storing,
    // and this is the one case where losing the record is better than keeping a nameless one
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new NewAuditEvent(
                "\n\r\t", "search", "authz", AuditEventType.DECISION_ALLOW, "default"));
  }

  @Test
  void shouldRejectNullFieldsWhenBuilt() {
    // given / when / then
    assertThrows(
        NullPointerException.class,
        () -> new NewAuditEvent(null, "search", "authz", AuditEventType.DECISION_ALLOW, "d"));
    assertThrows(
        NullPointerException.class,
        () -> new NewAuditEvent("agent-1", "search", "authz", AuditEventType.DECISION_ALLOW, null));
    assertThrows(
        NullPointerException.class,
        () -> new NewAuditEvent("agent-1", "search", "authz", null, "d"));
  }
}
