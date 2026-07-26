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
package io.github.tikyparkinson.mcpguardrails.trifecta.domain;

import java.util.Locale;

/**
 * One leg of the lethal trifecta. Each is harmless alone; together they let an instruction hidden
 * in a piece of read data make the agent send another piece of data out.
 *
 * <p>The names travel in the escalation reason, so they are part of the contract.
 */
public enum Capability {

  /** The tool reaches data the agent's counterpart is not supposed to publish. */
  PRIVATE_DATA,

  /** The tool brings in content nobody on this side wrote, which may carry instructions. */
  UNTRUSTED_CONTENT,

  /** The tool can put data somewhere outside, by any medium. */
  EXTERNAL_COMMS;

  /** Human-readable form used in reasons: {@code private data}, {@code untrusted content}. */
  public String describe() {
    return name().toLowerCase(Locale.ROOT).replace('_', ' ');
  }
}
