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
package io.github.tikyparkinson.mcpguardrails.egress.adapter.in.chain;

/**
 * Action taken when a declared egress tool aims at a destination outside the allowlist, or at one
 * that cannot be read.
 *
 * <p>There is deliberately no {@code ALLOW}: it would remove the fail-closed guarantee of the
 * module. {@code ESCALATE} does not weaken it either — the call is not executed.
 */
public enum ViolationAction {
  DENY,
  ESCALATE
}
