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
package io.github.tikyparkinson.mcpguardrails.injectionguard.application.port.out;

import io.github.tikyparkinson.mcpguardrails.injectionguard.domain.InjectionRule;
import java.util.List;

/**
 * Outbound port for the active rule set. Queried on every scan so implementations may serve dynamic
 * rule feeds.
 */
public interface InjectionRuleSetPort {

  /** Rules in force, in evaluation order. Never null; may be empty. */
  List<InjectionRule> activeRules();
}
