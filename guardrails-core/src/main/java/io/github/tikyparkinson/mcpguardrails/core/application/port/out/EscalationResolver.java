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
package io.github.tikyparkinson.mcpguardrails.core.application.port.out;

import io.github.tikyparkinson.mcpguardrails.core.domain.ChainVerdict;
import io.github.tikyparkinson.mcpguardrails.core.domain.EscalationOutcome;
import io.github.tikyparkinson.mcpguardrails.core.domain.ToolInvocationContext;

/**
 * SPI deciding what actually happens when the chain resolves {@code Escalate}.
 *
 * <p>With no implementation registered, an escalation returns an error to the agent, which is the
 * historical behaviour. With one, the invocation is held until this method answers.
 *
 * <p>This is not a {@link Guardrail} and does not take part in the chain. A guardrail contributes
 * to the verdict; this resolves one that is already final. The distinction matters: the decision
 * combiner keeps the first {@code Escalate} it finds, so no guardrail running afterwards could turn
 * an approval into {@code Allow} even if it wanted to.
 *
 * <p>Called synchronously on the MCP call thread: an implementation that waits for a human holds
 * that thread meanwhile, and it is the implementation's job to bound how long.
 */
public interface EscalationResolver {

  /** Resolves the escalation. Never returns null. */
  EscalationOutcome resolve(ToolInvocationContext context, ChainVerdict verdict);
}
