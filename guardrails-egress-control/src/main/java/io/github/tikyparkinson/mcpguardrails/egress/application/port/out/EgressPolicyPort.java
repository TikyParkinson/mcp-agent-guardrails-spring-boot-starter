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
package io.github.tikyparkinson.mcpguardrails.egress.application.port.out;

import io.github.tikyparkinson.mcpguardrails.egress.domain.EgressPolicy;

/**
 * Outbound port for the egress policy. Replace it to feed the allowlist from a CMDB, a service
 * catalog or a network API instead of the configuration-backed default.
 *
 * <p>It is queried on every evaluation, so the policy can change without a restart. In exchange, an
 * adapter backed by a remote system must cache with a TTL: otherwise its latency is added to every
 * tool call.
 */
public interface EgressPolicyPort {

  /** Policy in force. Never null; both lists may be empty, which denies every egress. */
  EgressPolicy currentPolicy();
}
