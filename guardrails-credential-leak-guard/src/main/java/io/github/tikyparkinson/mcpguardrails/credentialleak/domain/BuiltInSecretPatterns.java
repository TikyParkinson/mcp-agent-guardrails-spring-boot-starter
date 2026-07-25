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

import static io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretSeverity.CONFIRMED;
import static io.github.tikyparkinson.mcpguardrails.credentialleak.domain.SecretSeverity.SUSPECTED;

import java.util.List;

/**
 * The default detection set: eleven patterns covering the credential formats that actually travel
 * through tool calls.
 *
 * <p>The four patterns that need the key in front of the value ({@code aws-secret-access-key},
 * {@code connection-string-password}, {@code bearer-token}, {@code credential-assignment}) declare
 * a {@code secretGroup} so redaction removes the value and leaves the key readable.
 */
public final class BuiltInSecretPatterns {

  private static final List<SecretPattern> DEFAULTS =
      List.of(
          SecretPattern.of("aws-access-key-id", "AKIA[0-9A-Z]{16}", CONFIRMED),
          SecretPattern.of(
              "aws-secret-access-key",
              "(aws_secret_access_key\\s*[=:]\\s*)(\\S{40})",
              CONFIRMED,
              2),
          SecretPattern.of("openai-api-key", "sk-(?:proj-)?[A-Za-z0-9_-]{20,}", CONFIRMED),
          SecretPattern.of("github-token", "gh[pousr]_[A-Za-z0-9]{36,}", CONFIRMED),
          SecretPattern.of("slack-token", "xox[baprs]-[A-Za-z0-9-]{10,}", CONFIRMED),
          SecretPattern.of("google-api-key", "AIza[0-9A-Za-z_-]{35}", CONFIRMED),
          SecretPattern.of(
              "jwt", "eyJ[A-Za-z0-9_-]{8,}\\.eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}", CONFIRMED),
          SecretPattern.of(
              "private-key-block",
              "-----BEGIN (?:RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY-----",
              CONFIRMED),
          SecretPattern.of(
              "connection-string-password",
              "((?:postgres(?:ql)?|mysql|mongodb(?:\\+srv)?|redis|amqp)://[^:/\\s]+:)"
                  + "([^@\\s]+)(?=@)",
              CONFIRMED,
              2),
          SecretPattern.of("bearer-token", "(bearer\\s+)([A-Za-z0-9._~+/-]{16,})", CONFIRMED, 2),
          SecretPattern.of(
              "credential-assignment",
              "((?:password|passwd|pwd|secret|api[_-]?key|access[_-]?token)\\s*[=:]\\s*[\"']?)"
                  + "([^\\s\"',;]{8,})",
              SUSPECTED,
              2));

  private BuiltInSecretPatterns() {}

  /** The eleven built-in patterns, in evaluation order. */
  public static List<SecretPattern> defaults() {
    return DEFAULTS;
  }
}
