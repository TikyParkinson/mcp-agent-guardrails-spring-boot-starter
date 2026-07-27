# guardrails-credential-leak-guard obfuscation — APPROVED

**Date:** 2026-07-27
**Branch:** `bug/validation-0.2.0-findings`
**Findings closed:** F-2 and F-5 of the 0.2.0 validation report
**Spec:** [`guardrails-credential-leak-obfuscation-spec.md`](guardrails-credential-leak-obfuscation-spec.md)

## Coverage

```
guardrails-credential-leak-guard: 99.6 % lines (226/227), 98.6 % branches (71/72)
Reactor: 1009 tests, 0 failures, 0 errors
```

Threshold is 80/80.

## §9 checklist

| # | Check | Evidence |
|---|---|---|
| 1 | Code matches the spec | 7 design decisions, including three where the apparent symmetry with other modules is deliberately broken |
| 2 | No Spring in `domain`/`application` | 0 matches |
| 3 | GA versions | No RC or milestone |
| 4 | Spotless + Checkstyle | BUILD SUCCESS, 0 violations |
| 5 | Jacoco ≥ 80/80 | 99.6 % / 98.6 % |
| 6 | Testcontainers | **N/A** — the only outbound adapter is in-memory; the module holds patterns, not a store |
| 7 | Dependencies justified | The pom does not change. `java.util.Base64` ships with the JDK |
| 8 | Apache 2.0 header | 3/3 new files |
| 9 | README | Pluggable port, the new locations, and a section stating what the module does not catch |
| 10 | No method over ~25 lines | 0 |
| 11 | No `return null` | 0 — `decode` returns `Optional` |

## What was closed

**F-5 — credentials used as field names.** The inbound scanner walked values only, so
`{"payload":{"AKIA…":"valor"}}` was allowed on the way in and only caught on the way back out, by
which point the tool had already received it. Keys are now scanned at every level, reported as
`arguments.payload{AKIA…}` — the braces tell an investigator the secret was the name rather than
the contents, which are different incidents.

**F-2 — Base64.** A `.env` or configuration blob serialized into an argument used to sail past every
pattern, and it arrives that way without anyone trying to fool anyone. One level is decoded and
scanned, reported with a `(base64)` suffix. The decoded text is matched and dropped, never recorded.

Split and reversed secrets remain undetected **by decision**, and the README now says so with the
reasoning rather than leaving it to be discovered.

## Verified beyond the checklist

- **A real `.env` is multi-line.** Had the printability check rejected newlines, no `.env` would
  ever have been detected — that is, the exact scenario F-2 exists for. Covered by a test that
  decodes a three-line file.
- **Scanning keys doubles the values examined, and it was measured**: 105 flattened values instead
  of 53, 0.107 ms per invocation, and zero false positives across 53 ordinary field names.
- **The finding never carries the decoded secret**, asserted explicitly. A reason string reaches
  the model, so writing it there would reintroduce the leak the guardrail prevents.
- **Recursion is bounded**: Base64 inside Base64 loses one layer only, and the size ceiling is
  64 KB.

## Known trait, recorded rather than rejected

`Base64Decoder.isPrintable` opens with `if (decoded.isEmpty()) return false`, and those two lines
are unreachable today: `value.strip()` removes the only characters the MIME decoder ignores, and
the filter demands sixteen valid ones, so nothing that reaches the check can decode to nothing.

Not treated as a defect. It is a two-line guard with no runtime cost that keeps holding if someone
later relaxes the filter — for instance by lowering the minimum length. `test-engineer` reported it
instead of writing a contrived test to hide it, which is the right call: coverage bought with a
test that asserts nothing is worse than a line honestly uncovered.

## Verdict

**APPROVED.**
