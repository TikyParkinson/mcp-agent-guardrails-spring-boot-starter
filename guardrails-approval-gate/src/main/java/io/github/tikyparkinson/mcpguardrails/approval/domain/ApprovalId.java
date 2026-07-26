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
package io.github.tikyparkinson.mcpguardrails.approval.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier of an approval request.
 *
 * <p>Opaque and unguessable on purpose: this is what an approver presents to resolve a request, so
 * a predictable value would let a third party decide in their place.
 */
public record ApprovalId(String value) {

  public ApprovalId {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("ApprovalId value must not be blank");
    }
  }

  /** Generates a new random identifier. */
  public static ApprovalId newId() {
    return new ApprovalId(UUID.randomUUID().toString());
  }
}
