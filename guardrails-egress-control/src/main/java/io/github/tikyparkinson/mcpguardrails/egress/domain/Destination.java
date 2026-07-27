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
package io.github.tikyparkinson.mcpguardrails.egress.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * Normalized destination host: lower case, without the trailing dot of an FQDN and without the
 * brackets of an IPv6 literal.
 *
 * <p>{@code java.net.URI#getHost()} returns IPv6 addresses bracketed ({@code [::1]}), while an
 * allowlist is written the natural way ({@code ::1}); stripping them here is what makes both sides
 * comparable.
 */
public record Destination(String value) {

  public Destination {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
  }

  /** Normalizes and wraps a host. */
  public static Destination of(String host) {
    Objects.requireNonNull(host, "host");
    String normalized = normalize(host);
    if (normalized.isBlank()) {
      throw new IllegalArgumentException("host must not be blank");
    }
    return new Destination(normalized);
  }

  static String normalize(String host) {
    String normalized = host.strip().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("[") && normalized.endsWith("]")) {
      normalized = normalized.substring(1, normalized.length() - 1);
    }
    while (normalized.endsWith(".")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }
}
