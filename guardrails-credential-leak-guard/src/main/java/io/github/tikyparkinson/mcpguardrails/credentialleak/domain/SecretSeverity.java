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
package io.github.tikyparkinson.mcpguardrails.credentialleak.domain;

/**
 * Confidence that the detected value really is a credential.
 *
 * <p>{@code CONFIRMED} means the format is unmistakable (vendor prefix, JWT structure, PEM header);
 * {@code SUSPECTED} means a keyword heuristic matched and false positives are possible.
 */
public enum SecretSeverity {
  SUSPECTED,
  CONFIRMED
}
