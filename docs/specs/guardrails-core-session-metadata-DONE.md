# guardrails-core-session-metadata — APPROVED

**Date:** 2026-07-26
**Reviewed against:** ARCHITECTURE.md §9 checklist and the five conditions of §5.1
**Spec:** [`guardrails-core-session-metadata-spec.md`](guardrails-core-session-metadata-spec.md)

## Coverage

```
guardrails-core: Tests run: 119, Failures: 0, Errors: 0, Skipped: 0

LINE    100.0%  (284/284)
BRANCH  100.0%  (85/85)
```

114 of those tests existed before this extension and **none was modified** — `git diff
--name-status` over `guardrails-core/src/test` reports no `M`.

## §5.1 conditions

| Condition | Evidence |
|---|---|
| **Additive only** | One file changed, `+30 −1`. The single removed line is `Map.of()` inside `toContext`. All three public constructors are still there |
| **Neutral when unused** | `metadata` had no readers anywhere in the project before this — `grep "\.metadata()"` across all twelve modules returned 0. A consumer that ignores the new key behaves identically |
| **Motivated by a real consumer** | `guardrails-trifecta-correlator`, specified and built in the same branch |
| **Its own spec** | This document's spec, run through the five agents before its consumer |
| **Not a breaking change** | All 13 reactor modules `BUILD SUCCESS` with no changes to the 11 published ones |

## §9 checklist

| # | Check | Evidence |
|---|---|---|
| 1 | Spec exists and code matches | §5 implemented as written, including the absent-key rule |
| 2 | No Spring in `domain`/`application` | The change is in `adapter/in/mcp` |
| 3 | GA versions verified | No new dependencies |
| 4 | Spotless and Checkstyle clean | `0 Checkstyle violations` |
| 5 | Jacoco ≥80/80 | 100% / 100% |
| 6 | Testcontainers for a real store | **N/A** — no outbound store |
| 7 | Dependencies justified | None added; `mcp-core` was already a dependency |
| 8 | Apache 2.0 header | Present in the one new test file |
| 9 | README documents the port | Documented in the consumer module's README |
| 10 | No method over ~25 lines | `metadataOf` is 8 lines |
| 11 | No `return null` in `domain`/`application` | Not applicable to this layer; the method returns an empty map, never null |

## Verified beyond the checklist

- **Absence is a missing key, not an empty value.** Asserted for a null session, a blank one, and
  no exchange at all. A consumer that finds the key can trust there is a session instead of
  repeating the check — and one that forgets to repeat it would otherwise correlate everything
  under a session named `""`.
- **A transport that throws does not fail the invocation.** `sessionId()` is implemented outside
  this project; an exception there is treated as no session, and the tool still runs.
- **The key is a public constant.** A consumer writing the literal itself would turn a typo into a
  silent miss rather than a compile error.

## Verdict

**APPROVED.**
