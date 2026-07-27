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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Recovering text from a Base64 payload, and refusing to when it is not worth it. */
class Base64DecoderTest {

  private static final String SECRET = "sk-proj-A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6";

  @Test
  void shouldRecoverTheHiddenTextWhenValueIsBase64() {
    // given a credential encoded the way a serialized .env would arrive
    String encoded = encode("openai_key=" + SECRET);

    // when it is decoded
    // then the secret is back in a form the patterns can match
    assertEquals(Optional.of("openai_key=" + SECRET), Base64Decoder.decode(encoded));
  }

  @Test
  void shouldReturnEmptyWhenValueIsOrdinaryText() {
    // given a normal argument
    // when it is decoded
    // then nothing comes back: the common path must not allocate or guess
    assertTrue(Base64Decoder.decode("summarise the sales report").isEmpty());
  }

  @Test
  void shouldReturnEmptyWhenValueIsTooShortToBeWorthDecoding() {
    // given a short run of Base64-looking characters, which ordinary identifiers are full of
    // when it is decoded
    // then it is left alone
    assertTrue(Base64Decoder.decode("abcdef").isEmpty());
  }

  @Test
  void shouldReturnEmptyWhenDecodedBytesAreBinary() {
    // given Base64 of arbitrary bytes rather than of text
    String binary =
        Base64.getEncoder().encodeToString(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12});

    // when it is decoded
    // then it is discarded. Binary noise cannot hold a secret in text form and would only add
    // findings nobody can act on
    assertTrue(Base64Decoder.decode(binary).isEmpty());
  }

  @Test
  void shouldReturnEmptyWhenValueExceedsTheSizeCeiling() {
    // given an oversized payload
    String huge = encode("x".repeat(70 * 1024));

    // when it is decoded
    // then it is refused. A decoded payload is held in memory whole, so an unbounded limit would
    // let one argument cost more than the invocation it protects
    assertTrue(Base64Decoder.decode(huge).isEmpty());
  }

  @Test
  void shouldReturnEmptyWhenValueIsMalformedBase64() {
    // given seventeen valid characters, which is not a whole number of Base64 units
    // when it is decoded
    // then it comes back empty rather than throwing. A value that almost looks like Base64 is the
    // normal case in tool arguments, not an exceptional one
    assertTrue(Base64Decoder.decode("A".repeat(17)).isEmpty());
  }

  @Test
  void shouldNotDecodeTwiceWhenValueIsDoubleEncoded() {
    // given Base64 wrapped in Base64
    String doubled = encode(encode(SECRET));

    // when it is decoded
    // then only one layer comes off, so the inner payload is still encoded. Recursive decoding
    // without a bound is a decompression bomb, and double encoding is deliberate anyway
    assertFalse(Base64Decoder.decode(doubled).orElseThrow().contains(SECRET));
  }

  @Test
  void shouldRecoverAMultiLineDotEnvWhenDecoding() {
    // given an actual .env, which is several lines separated by newlines
    String dotEnv = "DB_HOST=db.internal\nOPENAI_KEY=" + SECRET + "\nDEBUG=false\n";

    // when it is decoded
    // then the newlines survive. Treating them as unprintable would reject every real .env, which
    // is the single most likely thing to arrive Base64-encoded
    assertEquals(Optional.of(dotEnv), Base64Decoder.decode(encode(dotEnv)));
  }

  @Test
  void shouldKeepTabsAndCarriageReturnsWhenDecoding() {
    // given text laid out with tabs and Windows line endings
    String text = "key\tvalue\r\ntoken\t" + SECRET + "\r\n";

    // when it is decoded
    // then it survives too: these are formatting, not binary noise
    assertEquals(Optional.of(text), Base64Decoder.decode(encode(text)));
  }

  @Test
  void shouldReturnEmptyWhenDecodedPayloadIsEmpty() {
    // given a run of newlines, which the MIME decoder treats as separators and decodes to nothing
    // when it is decoded
    // then it comes back empty: there is nothing for a pattern to match
    assertTrue(Base64Decoder.decode("\n".repeat(16)).isEmpty());
  }

  @Test
  void shouldRejectNullWhenDecoding() {
    // given / when / then
    assertThrows(NullPointerException.class, () -> Base64Decoder.decode(null));
  }

  private static String encode(String text) {
    return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
  }
}
