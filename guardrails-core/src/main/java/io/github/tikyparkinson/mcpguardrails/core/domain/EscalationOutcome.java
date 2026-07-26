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
package io.github.tikyparkinson.mcpguardrails.core.domain;

/**
 * Outcome of resolving an {@link Escalate} verdict: the invocation either runs or it does not.
 *
 * <p>There is no third, pending case. Waiting happens inside the resolver, not in its answer — a
 * caller holding this type has an answer by definition.
 */
public sealed interface EscalationOutcome permits ApprovedExecution, RejectedExecution {}
