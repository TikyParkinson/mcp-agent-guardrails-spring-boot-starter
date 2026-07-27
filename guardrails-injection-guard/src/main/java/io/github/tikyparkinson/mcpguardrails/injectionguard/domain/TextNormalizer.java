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
package io.github.tikyparkinson.mcpguardrails.injectionguard.domain;

import java.text.Normalizer;
import java.util.Map;
import java.util.Objects;

/**
 * Folds text down to a form where characters that look alike compare alike, so a rule written in
 * plain ASCII still fires on an argument dressed up to avoid it.
 *
 * <p>Two techniques, because neither covers the other. {@link Normalizer.Form#NFKC} handles
 * everything Unicode gives a compatibility decomposition to — full-width {@code ｉｇｎｏｒｅ},
 * mathematical {@code 𝐢gnore}, and the rest of roughly a thousand styled letters nobody would put
 * in a table by hand. It does <em>nothing</em> for Cyrillic or Greek: {@code і} (U+0456) and Latin
 * {@code i} are separate letters with no decomposition between them, they simply share a glyph. So
 * the ones drawn identically in ordinary fonts are folded here explicitly.
 *
 * <p>This is deliberately not the full Unicode TR39 confusables table. That lives in ICU4J and
 * costs about 13 MB, which is out of proportion for a module that otherwise weighs what its classes
 * weigh. If evasions ever show up in Armenian or Cherokee, the answer is to adopt ICU4J rather than
 * to keep growing the table below by hand.
 *
 * <p>The normalized text is only ever matched against, never stored or shown. Findings report the
 * path of the argument the agent actually sent.
 */
public final class TextNormalizer {

  /**
   * Characters drawn like a Latin letter but encoded as something else. Only glyph-identical pairs
   * belong here: folding a character that merely resembles another turns detection into guessing.
   */
  private static final Map<Character, Character> CONFUSABLES =
      Map.ofEntries(
          // Cyrillic, lower case
          Map.entry('\u0430', 'a'),
          Map.entry('\u0432', 'b'),
          Map.entry('\u0435', 'e'),
          Map.entry('\u043A', 'k'),
          Map.entry('\u043C', 'm'),
          Map.entry('\u043D', 'h'),
          Map.entry('\u043E', 'o'),
          Map.entry('\u0440', 'p'),
          Map.entry('\u0441', 'c'),
          Map.entry('\u0442', 't'),
          Map.entry('\u0443', 'y'),
          Map.entry('\u0445', 'x'),
          Map.entry('\u0456', 'i'),
          Map.entry('\u0458', 'j'),
          Map.entry('\u0455', 's'),
          Map.entry('\u0501', 'd'),
          Map.entry('\u051B', 'q'),
          Map.entry('\u051D', 'w'),
          // Cyrillic, upper case
          Map.entry('\u0410', 'A'),
          Map.entry('\u0412', 'B'),
          Map.entry('\u0415', 'E'),
          Map.entry('\u041A', 'K'),
          Map.entry('\u041C', 'M'),
          Map.entry('\u041D', 'H'),
          Map.entry('\u041E', 'O'),
          Map.entry('\u0420', 'P'),
          Map.entry('\u0421', 'C'),
          Map.entry('\u0422', 'T'),
          Map.entry('\u0423', 'Y'),
          Map.entry('\u0425', 'X'),
          Map.entry('\u0406', 'I'),
          Map.entry('\u0408', 'J'),
          Map.entry('\u0405', 'S'),
          // Greek, lower case
          Map.entry('\u03B1', 'a'),
          Map.entry('\u03B2', 'b'),
          Map.entry('\u03B5', 'e'),
          Map.entry('\u03B9', 'i'),
          Map.entry('\u03BA', 'k'),
          Map.entry('\u03BD', 'v'),
          Map.entry('\u03BF', 'o'),
          Map.entry('\u03C1', 'p'),
          Map.entry('\u03C4', 't'),
          Map.entry('\u03C5', 'u'),
          Map.entry('\u03C7', 'x'),
          // Greek, upper case
          Map.entry('\u0391', 'A'),
          Map.entry('\u0392', 'B'),
          Map.entry('\u0395', 'E'),
          Map.entry('\u0396', 'Z'),
          Map.entry('\u0397', 'H'),
          Map.entry('\u0399', 'I'),
          Map.entry('\u039A', 'K'),
          Map.entry('\u039C', 'M'),
          Map.entry('\u039D', 'N'),
          Map.entry('\u039F', 'O'),
          Map.entry('\u03A1', 'P'),
          Map.entry('\u03A4', 'T'),
          Map.entry('\u03A5', 'Y'),
          Map.entry('\u03A7', 'X'));

  private TextNormalizer() {}

  /**
   * Returns the text folded for comparison. Never null.
   *
   * <p>Character positions are preserved. NFKC can change the length — a {@code ﬁ} ligature becomes
   * two characters — and when it does, the original text is kept instead. Findings do not carry
   * offsets today, but the day one does, a shifted position would point at the wrong character and
   * nobody would connect it to a normalization written months earlier.
   */
  public static String normalize(String text) {
    Objects.requireNonNull(text, "text");
    String folded = Normalizer.normalize(text, Normalizer.Form.NFKC);
    if (codePoints(folded) != codePoints(text)) {
      folded = text;
    }
    return foldConfusables(folded);
  }

  private static int codePoints(String text) {
    return text.codePointCount(0, text.length());
  }

  private static String foldConfusables(String text) {
    StringBuilder folded = null;
    for (int i = 0; i < text.length(); i++) {
      Character latin = CONFUSABLES.get(text.charAt(i));
      if (latin == null) {
        continue;
      }
      if (folded == null) {
        folded = new StringBuilder(text);
      }
      folded.setCharAt(i, latin);
    }
    return folded == null ? text : folded.toString();
  }
}
