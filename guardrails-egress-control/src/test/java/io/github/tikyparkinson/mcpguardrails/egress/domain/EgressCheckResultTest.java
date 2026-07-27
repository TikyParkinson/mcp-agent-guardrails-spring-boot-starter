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
package io.github.tikyparkinson.mcpguardrails.egress.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EgressCheckResultTest {

  private static final Destination HOST = Destination.of("api.evil.com");

  @Test
  void shouldCopyDestinationsDefensivelyWhenAllowedConstructed() {
    // given
    List<Destination> mutable = new ArrayList<>(List.of(HOST));
    DestinationsAllowed allowed = new DestinationsAllowed(mutable);

    // when
    mutable.add(Destination.of("other.com"));

    // then
    assertEquals(1, allowed.destinations().size());
  }

  @Test
  void shouldRejectNullDestinationsWhenAllowedConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new DestinationsAllowed(null));
  }

  @Test
  void shouldCopyViolationsDefensivelyWhenViolationConstructed() {
    // given
    List<Destination> mutable = new ArrayList<>(List.of(HOST));
    EgressViolation violation = new EgressViolation(mutable, List.of());

    // when
    mutable.add(Destination.of("other.com"));

    // then
    assertEquals(1, violation.violations().size());
  }

  @Test
  void shouldCopyUndeterminedArgumentsDefensivelyWhenViolationConstructed() {
    // given
    List<String> mutable = new ArrayList<>(List.of("url"));
    EgressViolation violation = new EgressViolation(List.of(), mutable);

    // when
    mutable.add("other");

    // then
    assertEquals(List.of("url"), violation.undeterminedArguments());
  }

  @Test
  void shouldFailWhenViolationCarriesNoReason() {
    // given: a violation with nothing to report would be undiagnosable
    List<Destination> noHosts = List.of();
    List<String> noArguments = List.of();

    // when / then
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class, () -> new EgressViolation(noHosts, noArguments));
    assertEquals("a violation must carry at least one reason", error.getMessage());
  }

  @Test
  void shouldRejectNullViolationsWhenConstructed() {
    // given
    List<String> arguments = List.of("url");

    // when / then
    assertThrows(NullPointerException.class, () -> new EgressViolation(null, arguments));
  }

  @Test
  void shouldRejectNullUndeterminedArgumentsWhenConstructed() {
    // given
    List<Destination> hosts = List.of(HOST);

    // when / then
    assertThrows(NullPointerException.class, () -> new EgressViolation(hosts, null));
  }

  @Test
  void shouldBeEqualByValueWhenNotAnEgressToolConstructed() {
    // given / when / then
    assertEquals(new NotAnEgressTool(), new NotAnEgressTool());
  }
}
