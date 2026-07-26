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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads the destination host out of an argument value: a URL, an email address or a bare host.
 *
 * <p>URLs are parsed with {@link URI} rather than with a regular expression, so {@code
 * https://evil.com@good.com/x} resolves to {@code good.com}: the userinfo before the {@code @} is
 * the classic trap for a hand-rolled parser. As a side effect of the same parser, a host with
 * non-ASCII characters yields no host at all, so both the homograph {@code аpple.com} and the
 * legitimate IDN {@code josé.example.com} come back as {@link NotDeterminable} and are therefore
 * denied.
 */
public final class DestinationExtractor {

  private static final Pattern HOST =
      Pattern.compile("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*");
  private static final Pattern IPV6 = Pattern.compile("\\[?[0-9a-f:]+]?");

  private DestinationExtractor() {}

  /** Extracts the destination, modelling the failure instead of returning null. */
  public static DestinationExtraction extract(String rawValue) {
    Objects.requireNonNull(rawValue, "rawValue");
    String candidate = rawValue.strip();
    if (candidate.isEmpty()) {
      return new NotDeterminable("empty");
    }
    Optional<String> uriHost = hostOfUri(candidate);
    if (uriHost.isPresent()) {
      return new Extracted(Destination.of(uriHost.get()));
    }
    int at = candidate.lastIndexOf('@');
    String bare = at >= 0 ? candidate.substring(at + 1) : candidate;
    String normalized = Destination.normalize(bare);
    if (isHost(normalized)) {
      return new Extracted(new Destination(normalized));
    }
    return new NotDeterminable("not-a-host");
  }

  /**
   * The host of the value when it is a URL with one.
   *
   * <p>Empty covers both failures of the parser and the ones it reports without complaining: an
   * internationalized host makes {@code getHost()} yield nothing at all.
   */
  private static Optional<String> hostOfUri(String candidate) {
    try {
      return Optional.ofNullable(new URI(candidate).getHost());
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
  }

  private static boolean isHost(String normalized) {
    if (normalized.isBlank()) {
      return false;
    }
    return HOST.matcher(normalized).matches() || IPV6.matcher(normalized).matches();
  }
}
