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

import java.util.Objects;

/**
 * No destination could be read from the argument, which the use case treats as a violation: an
 * unreadable destination on a declared egress tool is exactly what an obfuscated target looks like.
 *
 * @param rawValueKind short, non-sensitive hint of what was found ({@code "empty"}, {@code
 *     "not-a-host"}), never the value itself
 */
public record NotDeterminable(String rawValueKind) implements DestinationExtraction {

  public NotDeterminable {
    Objects.requireNonNull(rawValueKind, "rawValueKind");
    if (rawValueKind.isBlank()) {
      throw new IllegalArgumentException("rawValueKind must not be blank");
    }
  }
}
