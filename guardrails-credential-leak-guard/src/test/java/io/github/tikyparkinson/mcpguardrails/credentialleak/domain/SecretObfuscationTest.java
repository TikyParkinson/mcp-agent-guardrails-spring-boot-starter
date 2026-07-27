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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * F-2 and F-5 of VALIDATION-0.2.0.md, end to end against the built-in patterns: a credential used
 * as a field name, and one hidden inside a Base64 payload.
 */
class SecretObfuscationTest {

  private static final String OPENAI_KEY = "sk-proj-A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6";
  private static final String AWS_KEY = "AKIAIOSFODNN7EXAMPLE";
  private static final List<SecretPattern> PATTERNS = BuiltInSecretPatterns.defaults();

  @Test
  void shouldDetectACredentialUsedAsANestedMapKeyWhenScanning() {
    // given the exact shape from the report, where the secret is the field name
    SecretScanResult result = scan(Map.of("payload", Map.of(AWS_KEY, "valor")));

    // when the findings are read
    // then it is caught. Before this the inbound scanner only looked at values, and the leak was
    // caught on the way back out — by then the tool had already received it
    assertFalse(result.clean());
  }

  @Test
  void shouldReportKeysWithBracesWhenTheSecretIsAFieldName() {
    // given a secret as a field name
    SecretScanResult result = scan(Map.of("payload", Map.of(AWS_KEY, "valor")));

    // when the location is read
    // then the braces say it was the name and not the contents. They are different incidents for
    // whoever investigates, and the location is all they get
    assertEquals("arguments.payload{" + AWS_KEY + "}", result.findings().get(0).location());
  }

  @Test
  void shouldDetectACredentialUsedAsATopLevelKeyWhenScanning() {
    // given the secret as the name of a tool argument itself
    SecretScanResult result = scan(Map.of(AWS_KEY, "valor"));

    // when the findings are read
    // then it is caught too: the top level is a map like any other
    assertFalse(result.clean());
  }

  @Test
  void shouldDetectACredentialHiddenInBase64WhenScanning() {
    // given a configuration blob serialized the way a .env would be
    SecretScanResult result = scan(Map.of("blob", encode("openai_key=" + OPENAI_KEY)));

    // when the findings are read
    // then it is caught. This one happens without anyone trying to fool anyone, which is why it
    // is worth decoding at all
    assertFalse(result.clean());
  }

  @Test
  void shouldMarkDecodedFindingsWithASuffixWhenTheSecretWasEncoded() {
    // given a Base64 argument carrying a credential
    SecretScanResult result = scan(Map.of("blob", encode("openai_key=" + OPENAI_KEY)));

    // when the location is read
    // then the suffix is the only trace that decoding happened; the decoded text is scanned and
    // dropped, never recorded, exactly like the original
    assertEquals("arguments.blob(base64)", result.findings().get(0).location());
  }

  @Test
  void shouldDetectACredentialInBase64NestedInsideStructuresWhenScanning() {
    // given the encoded blob buried in a nested map
    SecretScanResult result =
        scan(Map.of("cfg", Map.of("inner", encode("openai_key=" + OPENAI_KEY))));

    // when the findings are read
    // then depth and decoding compose
    assertEquals("arguments.cfg.inner(base64)", result.findings().get(0).location());
  }

  @Test
  void shouldNeverPutTheDecodedSecretInTheFindingWhenScanning() {
    // given a Base64 argument carrying a credential
    SecretScanResult result = scan(Map.of("blob", encode("openai_key=" + OPENAI_KEY)));

    // when everything the finding carries is inspected
    // then the secret is nowhere in it. A reason string reaches the model, so writing the secret
    // there would reintroduce the very leak this guardrail exists to prevent
    assertFalse(result.findings().get(0).toString().contains(OPENAI_KEY));
  }

  @Test
  void shouldNotFireOnOrdinaryArgumentsWhenScanning() {
    // given a call with nothing sensitive in it, keys included
    SecretScanResult result =
        scan(
            Map.of(
                "id",
                "customer-1234",
                "timestamp",
                "2026-07-27T10:00:00Z",
                "url",
                "https://api.example.com/v1/data"));

    // when the findings are read
    // then nothing fires. Scanning keys doubles the values examined, and it is only worth it if
    // it does not start denying legitimate calls
    assertTrue(result.clean());
  }

  @Test
  void shouldNotFireOnASplitSecretWhenScanning() {
    // given a credential broken in two, which the receiver could rejoin
    SecretScanResult result = scan(Map.of("text", "sk-proj-A1b2C3d4E5f6 G7h8I9j0K1l2M3n4O5p6"));

    // when the findings are read
    // then nothing fires, and that is a documented boundary rather than an oversight: chasing
    // permutations of every value would make the guardrail unusable through false positives
    assertTrue(result.clean());
  }

  @Test
  void shouldDetectACredentialNestedPastTheOldDepthLimitWhenScanning() {
    // given the secret buried twelve levels down — the shape from F-10, which used to sail past
    Object nested = AWS_KEY;
    for (int level = 0; level < 12; level++) {
      nested = Map.of("l" + level, nested);
    }

    // when the arguments are scanned
    // then it is caught. The outbound chain would have redacted it on the way back, but a tool
    // that uses the credential rather than echoing it had already received one
    assertFalse(scan(Map.of("payload", nested)).clean());
  }

  @Test
  void shouldDetectACredentialNestedInDeepListsWhenScanning() {
    // given the same trick with lists instead of maps
    Object nested = AWS_KEY;
    for (int level = 0; level < 12; level++) {
      nested = List.of(nested);
    }

    // when the arguments are scanned
    // then it is caught too: the bypass was never specific to maps
    assertFalse(scan(Map.of("payload", nested)).clean());
  }

  @Test
  void shouldReportAnIncompleteScanRatherThanCleanWhenTheBudgetRunsOut() {
    // given a structure past the budget, with no secret in the part that gets scanned
    Object nested = "harmless";
    for (int level = 0; level < 70; level++) {
      nested = Map.of("l" + level, nested);
    }
    SecretScanResult result = scan(Map.of("payload", nested));

    // when the outcome is read
    // then nothing matched but the scan says it did not finish. The guardrail turns that into a
    // Deny — the whole point of F-10 is that these two are not the same answer
    assertTrue(result.clean());
    assertFalse(result.complete());
  }

  private static SecretScanResult scan(Map<String, Object> arguments) {
    return SecretScanner.scan(arguments, PATTERNS, "arguments");
  }

  private static String encode(String text) {
    return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
  }
}
