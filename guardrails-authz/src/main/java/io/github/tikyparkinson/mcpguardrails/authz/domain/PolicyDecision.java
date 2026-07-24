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
package io.github.tikyparkinson.mcpguardrails.authz.domain;

import java.util.Objects;

/**
 * Outcome of evaluating the policy for one invocation: the effect plus where it came from ({@code
 * "rule[i]"} for the matching rule, {@code "default"} for the policy default).
 */
public record PolicyDecision(PermissionEffect effect, String source) {

  public PolicyDecision {
    Objects.requireNonNull(effect, "effect");
    Objects.requireNonNull(source, "source");
    if (source.isBlank()) {
      throw new IllegalArgumentException("source must not be blank");
    }
  }
}
