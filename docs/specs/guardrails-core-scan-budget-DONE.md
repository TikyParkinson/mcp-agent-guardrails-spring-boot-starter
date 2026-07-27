# guardrails-core scan budget — APPROVED

**Date:** 2026-07-27
**Branch:** `bug/validation-0.2.0-findings`
**Raised by:** SonarCloud on PR #13 — 44 duplicated lines, two byte-identical `ScanBudget` records
**Spec:** [`guardrails-core-scan-budget-spec.md`](guardrails-core-scan-budget-spec.md)

## Coverage

```
guardrails-core:                  100 % lines (303/303), 100 % branches (89/89)
guardrails-injection-guard:       100 % lines (205/205), 100 % branches (48/48)
guardrails-credential-leak-guard: 100 % lines (263/263), 100 % branches (82/82)
Reactor: 1030 tests, 0 failures, 0 errors
```

Threshold is 80/80.

## §5.1 checklist

| Condition | Evidence |
|---|---|
| Additive only | One new record in `core.domain`. No existing core type changed signature or semantics; the 127 pre-existing core tests pass untouched. |
| Neutral when unused | An immutable value with no side effects. No bean, no registration, no behavioural difference for a guardrail that ignores it. |
| Motivated by a real consumer | Two, both implemented in this branch. |
| Its own spec | This document's spec. |
| Not a breaking change | The two per-module `ScanBudget` types existed only inside this branch, never in a published version. |

## §9 checklist

| # | Check | Evidence |
|---|---|---|
| 1 | Code matches the spec | 4 design decisions |
| 2 | No Spring in `domain`/`application` | 0 matches in core, injection-guard, credential-leak |
| 3 | GA versions | No dependency changed |
| 4 | Spotless + Checkstyle | BUILD SUCCESS, 0 violations |
| 5 | Jacoco ≥ 80/80 | 100/100 on all three modules |
| 6 | Testcontainers | **N/A** — a value object has no store |
| 7 | Dependencies justified | No pom changed: both modules already depended on core |
| 8 | Apache 2.0 header | 2/2 new files |
| 9 | README | Core lists the type and why it lives there; both consumers link to it |
| 10 | No method over ~25 lines | 0 |
| 11 | No `return null` | 0 |

## What was closed

**Duplication.** `ScanBudget` was declared twice, byte for byte identical except the package line.
Moved to `io.github.tikyparkinson.mcpguardrails.core.domain`; both copies deleted.

The duplication was the symptom. The reason to share the type is that the two budgets **have to
agree**: both guardrails walk the same argument map in the same invocation, so two different limits
would truncate the same payload at two different points — invisible to an operator, measurable by
an attacker.

**Coverage.** `ScanBudget`'s two validation branches had no test at all, in either copy. The single
type now has `ScanBudgetTest`, seven cases covering both rejections, both signs, and the shared
default.

## Verified beyond the checklist

- **The reversal is recorded where the original decision was made.** `guardrails-scan-budget-spec.md`
  §2 and design decision 6 said the opposite — that a third consumer would be needed before moving
  it to core. Both now carry the correction and why the original argument was wrong, rather than
  being quietly rewritten.
- **No dependency between guardrails was introduced.** Both modules already had
  `guardrails-core`; `git diff` shows no `pom.xml` in the change.
- **`anomaly-detector` deliberately keeps its own `MAX_DEPTH = 8`.** It canonicalizes arguments
  into a fingerprint rather than searching them, so truncating makes similar calls collide, which
  increases detection. Same concept, opposite criterion.

## Verdict

**APPROVED.**
