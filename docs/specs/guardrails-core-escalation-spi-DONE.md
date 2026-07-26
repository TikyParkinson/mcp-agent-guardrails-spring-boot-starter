# guardrails-core-escalation-spi — APPROVED

**Date:** 2026-07-26
**Reviewed against:** ARCHITECTURE.md §9 checklist and the five conditions of §5.1
**Spec:** [`guardrails-core-escalation-spi-spec.md`](guardrails-core-escalation-spi-spec.md)

## Coverage

```
guardrails-core: Tests run: 114, Failures: 0, Errors: 0, Skipped: 0

LINE    100.0%  (278/278)
BRANCH  100.0%  (79/79)
```

99 of those tests existed before this extension and **none of them was modified**
(`git diff --name-status` over `guardrails-core/src/test` reports no `M`).

## §5.1 conditions

| Condition | Evidence |
|---|---|
| **Additive only** | 4 new files. The one modified file, `GuardedToolCallHandler`, is `+68 −7`; the 7 removed lines are inside the body of `apply`. Both pre-existing public constructors are still there — `grep -c "public GuardedToolCallHandler("` returns 3, the two old ones plus a new overload |
| **Neutral when unused** | With no resolver registered the escalation branch produces the byte-identical message it did before, asserted in `shouldReturnErrorWithoutRunningToolWhenNoResolverIsRegistered`. The 99 pre-existing tests pass untouched |
| **Motivated by a real consumer** | `guardrails-approval-gate`, specified and built in the same branch |
| **Its own spec** | This document's spec, run through the five agents before its consumer |
| **Not a breaking change** | All 12 reactor modules `BUILD SUCCESS` with no changes to the 9 published ones |

## §9 checklist

| # | Check | Evidence |
|---|---|---|
| 1 | Spec exists and code matches | §3–§5 implemented type by type |
| 2 | No Spring in `domain`/`application` | grep: **0** |
| 3 | GA versions verified | No new dependencies |
| 4 | Spotless and Checkstyle clean | `0 Checkstyle violations` |
| 5 | Jacoco ≥80/80 | 100% / 100% |
| 6 | Testcontainers for a real store | **N/A** — no outbound store |
| 7 | Dependencies justified | None added |
| 8 | Apache 2.0 header | 4 of 4 new files |
| 9 | README documents the port | Documented in the consumer module's README |
| 10 | No method over ~25 lines | Automated scan: none |
| 11 | No `return null` in `domain`/`application` | grep: **0** |

## Verified beyond the checklist

- **Fail-closed on a failing resolver.** `safeResolve` turns both a thrown `RuntimeException` and
  a `null` answer into `RejectedExecution`, each with its own test. An approval channel that is
  down cannot become a way through.
- **Approval does not bypass the outbound chain.** The approved branch calls `guardedDelegate`,
  not `delegate`. Two tests hold this: with `Redact` the result is still sanitized, with `Block`
  it is still blocked. Approving that a tool runs is not approving that its output is seen raw.
- **The resolver receives the whole verdict.** Asserted to carry every guardrail's evaluation, not
  just the one that won the combination.

## Verdict

**APPROVED.**
