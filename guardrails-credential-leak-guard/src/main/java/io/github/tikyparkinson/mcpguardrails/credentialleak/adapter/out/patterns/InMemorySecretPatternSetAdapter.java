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

import io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.out.SecretPatternSetPort;
import io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretPattern;
import java.util.List;
import java.util.Objects;

/**
 * Default pattern set: a fixed list resolved once at startup, typically the built-in patterns plus
 * whatever the configuration adds.
 *
 * <p>Replace this bean to feed patterns from a secret manager; see the module README.
 */
public final class InMemorySecretPatternSetAdapter implements SecretPatternSetPort {

  private final List<SecretPattern> patterns;

  public InMemorySecretPatternSetAdapter(List<SecretPattern> patterns) {
    this.patterns = List.copyOf(Objects.requireNonNull(patterns, "patterns"));
  }

  @Override
  public List<SecretPattern> activePatterns() {
    return patterns;
  }
}
