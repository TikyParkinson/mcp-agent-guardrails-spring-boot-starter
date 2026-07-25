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
package io.github.tikyparkinson.mcpguardrails.credentialleak.adapter.out.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.BuiltInSecretPatterns;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretPattern;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretSeverity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemorySecretPatternSetAdapterTest {

  @Test
  void shouldReturnTheConfiguredPatternsWhenAsked() {
    // given
    List<SecretPattern> patterns = BuiltInSecretPatterns.defaults();

    // when
    InMemorySecretPatternSetAdapter adapter = new InMemorySecretPatternSetAdapter(patterns);

    // then
    assertEquals(patterns, adapter.activePatterns());
  }

  @Test
  void shouldCopyThePatternsDefensivelyWhenConstructed() {
    // given
    List<SecretPattern> mutable =
        new ArrayList<>(List.of(SecretPattern.of("a", "a", SecretSeverity.CONFIRMED)));
    InMemorySecretPatternSetAdapter adapter = new InMemorySecretPatternSetAdapter(mutable);

    // when
    mutable.add(SecretPattern.of("b", "b", SecretSeverity.CONFIRMED));

    // then
    assertEquals(1, adapter.activePatterns().size());
  }

  @Test
  void shouldReturnEmptyWhenConfiguredWithNoPatterns() {
    // given / when
    InMemorySecretPatternSetAdapter adapter = new InMemorySecretPatternSetAdapter(List.of());

    // then
    assertEquals(List.of(), adapter.activePatterns());
  }

  @Test
  void shouldRejectNullPatternsWhenConstructed() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> new InMemorySecretPatternSetAdapter(null));
  }
}
