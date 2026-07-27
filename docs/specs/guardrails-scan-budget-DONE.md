# Scan budget — APPROVED

**Date:** 2026-07-27
**Branch:** `bug/validation-0.2.0-findings`
**Findings closed:** F-10 (High) and F-11 (Low) of the 0.2.0 validation report
**Spec:** [`guardrails-scan-budget-spec.md`](guardrails-scan-budget-spec.md)

## Coverage

```
guardrails-credential-leak-guard: 98.2 % lines (267/272), 95.5 % branches (84/88)
guardrails-injection-guard:       97.7 % lines (209/214), 92.6 % branches (50/54)
Reactor: 1016 tests, 0 failures, 0 errors
```

Threshold is 80/80.

## §9 checklist

| # | Check | Evidence |
|---|---|---|
| 1 | Code matches the spec | 7 design decisions |
| 2 | No Spring in `domain`/`application` | 0 matches in both modules |
| 3 | GA versions | No RC or milestone |
| 4 | Spotless + Checkstyle | BUILD SUCCESS, 0 violations |
| 5 | Jacoco ≥ 80/80 | 98.2/95.5 and 97.7/92.6 |
| 6 | Testcontainers | **N/A** — neither module has an outbound store |
| 7 | Dependencies justified | No pom gains anything; the only change is the `guardrails-audit` line F-1 removed |
| 8 | Apache 2.0 header | 3/3 new files |
| 9 | README | Both document the new bound, its properties, and why it counts nodes |
| 10 | No method over ~25 lines | 0 |
| 11 | No `return null` | 0 |

## What was closed

**F-10.** Both guardrails stopped at depth 8 and answered `Allow` for anything below, so nine
layers of nesting skipped them entirely. Verified against the running code:

```
12 levels → detected            (used to pass)
70 levels → clean=true, complete=false → the guardrail denies
```

The fix is not a bigger number. The bound moved to **node count**, which is what actually costs,
and running out of it now **denies** instead of allowing.

**F-11.** Documented next to the other limits of `credential-leak-guard` rather than raised:
moving the ceiling only moves where an attacker sits just outside of it.

## Verified beyond the checklist

- **The limit protected nothing it was supposed to.** Measured before designing: a thousand levels
  of nesting flatten to nine values and cost the same as eight, while ten thousand flat fields cost
  ten milliseconds and had no bound at all. The old rule forbade the cheap shape and allowed the
  expensive one.
- **The specific reason wins over the generic one.** A scan that both found a credential and ran
  out of budget reports the credential. Telling an agent "too large" when it just tried to send a
  key would invite it to retry with a different shape.
- **Backwards compatibility was broken and restored.** Adding two components to the two
  `@ConfigurationProperties` records removed their previous constructors, which is the same mistake
  `Allow` avoided earlier in this branch by keeping a convenience constructor. Caught by
  `test-engineer`, returned to `adapter-builder`, fixed the same way. Verified that the old
  four-argument form still compiles and yields the default budget.
- **`anomaly-detector` was deliberately left alone** despite sharing the constant. Its walk
  canonicalizes arguments into a fingerprint rather than searching them, so truncating makes two
  different calls collide into the same fingerprint — which increases detection.

## Verdict

**APPROVED.**
