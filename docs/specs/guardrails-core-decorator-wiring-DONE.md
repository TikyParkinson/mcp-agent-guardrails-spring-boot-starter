# guardrails-core-decorator-wiring — APPROVED

**Date:** 2026-07-27
**Reviewed against:** ARCHITECTURE.md §9 checklist and the five conditions of §5.1
**Spec:** [`guardrails-core-decorator-wiring-spec.md`](guardrails-core-decorator-wiring-spec.md)

## Coverage

```
guardrails-core: Tests run: 120, Failures: 0, Errors: 0, Skipped: 0

LINE    100.0%  (288/288)
BRANCH  100.0%  (85/85)
```

118 of those tests existed before this extension and **none was modified** — `git diff
--name-status` over `guardrails-core/src/test` reports no `M`. The two new ones cover the new
overload directly, in core, rather than only through the starter that consumes it.

## §5.1 conditions

| Condition | Evidence |
|---|---|
| **Additive only** | One production file changed, `+28 −0`. Nothing removed. The 4-argument and 5-argument overloads are untouched and still at lines 31 and 46 |
| **Neutral when unused** | A caller that does not pass an `EscalationResolver` keeps using the existing overloads and behaves identically. `null` is explicitly accepted and documented as preserving the historical behaviour |
| **Motivated by a real consumer** | `guardrails-approval-gate` could not be wired at all: `grep EscalationResolver` over `GuardrailToolDecorator` returned 0 before this change, while `GuardedToolCallHandler` already had a 6-argument constructor accepting one. The capability existed but was unreachable from the wiring layer |
| **Its own spec** | This document's spec, written and reviewed before its consumer was wired |
| **Not a breaking change** | All 13 reactor modules `BUILD SUCCESS`; the 11 already-published modules needed no change |

## §9 checklist

| # | Check | Evidence |
|---|---|---|
| 1 | Spec exists and code matches | The overload is exactly the signature in the spec |
| 2 | No Spring in `domain`/`application` | The change is in `adapter/in/mcp`; `grep org.springframework` over both layers returns 0 |
| 3 | GA versions verified | No new dependencies |
| 4 | Spotless and Checkstyle clean | `0 Checkstyle violations` |
| 5 | Jacoco ≥80/80 | 100% / 100% |
| 6 | Testcontainers for a real store | **N/A** — no outbound store |
| 7 | Dependencies justified | None added; `EscalationResolver` already lived in core |
| 8 | Apache 2.0 header | The file already had it; no new files |
| 9 | README documents the port | Documented in `spring-boot-starter/README.md`, the module that wires it |
| 10 | No method over ~25 lines | The new overload is 14 lines |
| 11 | No `return null` in `domain`/`application` | Not applicable to this layer |

## Verified beyond the checklist

- **The overload is what makes an escalation reach a person.** Asserted end to end: a chain that
  returns `Escalate` plus a resolver that approves results in the tool actually running
  (`isError() == false`, body `sent`). Without it, the same invocation returns an error to the
  agent — fail-closed, but indistinguishable from a failure.
- **The gap was found by grep, not assumed.** `GuardedToolCallHandler` accepted an
  `EscalationResolver` since the escalation-spi extension; only the decorator did not expose it.
  The fix restores reachability rather than adding capability.
- **The coverage gap was real and is closed.** When first written, the overload had no test in
  core: `guardrails-core` sat at 98.6% lines (284/288), and the 4 uncovered lines were exactly its
  body. Measuring only the consuming module's coverage had hidden it.

## Verdict

**APPROVED.**
