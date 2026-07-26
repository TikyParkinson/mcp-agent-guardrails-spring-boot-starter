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

import java.util.Objects;

/**
 * What a session looks like after an invocation contributed to it, and whether it was already
 * complete beforehand.
 *
 * <p>The second half cannot be worked out from the first. A session holding all three legs after an
 * invocation that contributed external comms looks exactly the same whether that invocation
 * supplied the missing leg or merely repeated one already there. Only whoever held the previous
 * state knows, and only inside the same atomic step — which is why the outbound port reports it
 * instead of letting the caller guess.
 *
 * @param session the session's state after accumulating
 * @param completeBefore whether the three legs already met before this invocation
 */
public record SessionAccumulation(SessionCapabilities session, boolean completeBefore) {

  public SessionAccumulation {
    Objects.requireNonNull(session, "session");
    if (completeBefore && !session.hasTrifecta()) {
      throw new IllegalArgumentException(
          "a session complete before cannot be incomplete after: capabilities are never removed");
    }
  }

  /** True when this invocation is the one that closed the triangle. */
  public boolean closedNow() {
    return session.hasTrifecta() && !completeBefore;
  }
}
