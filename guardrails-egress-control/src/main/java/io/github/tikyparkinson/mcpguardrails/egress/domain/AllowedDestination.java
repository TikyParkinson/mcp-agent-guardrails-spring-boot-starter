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

import java.util.Objects;

/**
 * One allowlist entry, either an exact host ({@code api.github.com}) or a subdomain wildcard
 * ({@code *.internal.corp}).
 *
 * <p>Matching compares whole domain labels, never substrings: {@code *.example.com} accepts {@code
 * a.example.com} and {@code a.b.example.com}, but rejects the apex {@code example.com} (list it
 * separately, as with certificates and cookies), {@code example.com.evil.com} and {@code
 * notexample.com}. A naive {@code endsWith} would accept the last two, which is precisely the
 * bypass an attacker would try.
 */
public record AllowedDestination(String pattern, boolean wildcard) {

  private static final String WILDCARD_PREFIX = "*.";

  public AllowedDestination {
    Objects.requireNonNull(pattern, "pattern");
    if (pattern.isBlank()) {
      throw new IllegalArgumentException("pattern must not be blank");
    }
  }

  /** Parses an allowlist entry, normalizing it the same way destinations are normalized. */
  public static AllowedDestination of(String rawPattern) {
    Objects.requireNonNull(rawPattern, "rawPattern");
    String normalized = Destination.normalize(rawPattern);
    if (normalized.startsWith(WILDCARD_PREFIX)) {
      return wildcardEntry(rawPattern, normalized);
    }
    if (normalized.contains("*")) {
      throw new IllegalArgumentException(
          "'*' is only allowed as the leading label of a pattern, was: " + rawPattern);
    }
    if (normalized.isBlank()) {
      throw new IllegalArgumentException("pattern must not be blank");
    }
    return new AllowedDestination(normalized, false);
  }

  private static AllowedDestination wildcardEntry(String rawPattern, String normalized) {
    String suffix = normalized.substring(WILDCARD_PREFIX.length());
    if (suffix.contains("*")) {
      throw new IllegalArgumentException(
          "a wildcard pattern must be '*.' followed by a host, was: " + rawPattern);
    }
    return new AllowedDestination(normalized, true);
  }

  /** True when this entry permits the given destination. */
  public boolean matches(Destination destination) {
    Objects.requireNonNull(destination, "destination");
    String host = destination.value();
    if (!wildcard) {
      return host.equals(pattern);
    }
    String suffix = pattern.substring(1);
    return host.endsWith(suffix) && host.length() > suffix.length();
  }
}
