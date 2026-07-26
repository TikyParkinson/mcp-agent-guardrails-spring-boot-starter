# guardrails-approval-gate — APPROVED

**Date:** 2026-07-26
**Reviewed against:** ARCHITECTURE.md §9 checklist
**Spec:** [`guardrails-approval-gate-spec.md`](guardrails-approval-gate-spec.md)
**Prerequisite:** [`guardrails-core-escalation-spi-DONE.md`](guardrails-core-escalation-spi-DONE.md)

## Coverage

```
Tests run: 96, Failures: 0, Errors: 0, Skipped: 0     BUILD SUCCESS

LINE    100.0%  (153/153)
BRANCH  100.0%  (54/54)
METHOD  100.0%  (36/36)
```

Threshold required by §8 is 80% lines and 80% branches. Full reactor (12 modules):
`BUILD SUCCESS`.

## Checks

| # | Check | Evidence |
|---|---|---|
| 1 | Spec exists, code does not drift from it | §2–§7 matched class by class; the six design decisions are in §9 |
| 2 | No `org.springframework` in `domain`/`application` | `grep` over both packages: **0** results |
| 3 | Dependency versions are verified GA | Spring Boot `4.1.0`, JUnit `6.1.2`, Mockito `5.23.0` — all HTTP 200 on Maven Central. No RC, milestone or unresolved version TODO |
| 4 | Spotless and Checkstyle clean | `0 Checkstyle violations`, `BUILD SUCCESS` |
| 5 | Jacoco ≥80% lines and branches | 100% / 100% (above) |
| 6 | Testcontainers for a real store adapter | **N/A** — the only outbound adapter is in-process. §8 requires it only when a real store exists |
| 7 | Every `<dependency>` justified in spec §7 | 4 declared, 4 listed: `guardrails-core`, `spring-boot` (provided), `junit-jupiter`, `mockito-core`. None added |
| 8 | Apache 2.0 header on every `.java` | 19 of 19 files |
| 9 | README documents the pluggable port | §"Replacing the channel" names `ApprovalRequestPort`, its four methods, the default adapter's real limitation, and the two properties a replacement must honour |
| 10 | No production method over ~25 lines or mixing responsibilities | Automated scan: none |
| 11 | No `return null` in `domain`/`application` | `grep`: **0**. Absence is modelled by `Optional` on the port and by `Rejected` in the decision type |

## Verified beyond the checklist

- **ARCHITECTURE.md §5 and §5.2 respected.** No import of any `guardrails-*` module other than
  core; the module declares its own `ApprovalRequestPort` rather than borrowing one.
- **No path produces an approval on its own.** Exercised across every combination of a saturated
  channel, an expired deadline and an explicit refusal: zero approvals. This is the property the
  module exists for, and it is measured rather than asserted in prose.
- **The wait does not serialize.** Ten concurrent 200 ms waits finish in well under five times one
  of them. Had the wait been taken under a lock — the mistake spec §5.2 warns about — they would
  have taken ten times as long and the quotas would have been decoration. `grep synchronized` over
  the adapter returns 0.
- **Quotas hold under contention.** Fifty concurrent submissions for ten slots admit exactly ten;
  the check and the increment happen inside `ConcurrentHashMap.compute`, which is atomic per key.
- **A refused submission returns its global slot.** Twenty per-agent refusals leave the other nine
  global slots free, so repeated refusals cannot starve a nearly empty channel.
- **The interrupt flag survives a cancelled wait**, so a server shutting down still sees the
  interruption it requested.

## Noted, not blocking

- `InMemoryApprovalRequestAdapter` exposes `pendingCount()` and `clear()`, which spec §5.2 does not
  list. Both are adapter-local observability, outside the port contract, following the precedent
  of `InMemoryEgressPolicyAdapter.currentPolicy()` and `InMemoryInvocationHistoryAdapter`.
- The module holds a thread per pending approval. This is inherent to the synchronous MCP handler
  and is documented in spec §9 decision 4, in the README, and bounded by the two quotas — but it
  is the property to revisit first if the project ever adopts the SDK's asynchronous mode.
- `credential-leak-guard` configured with `ESCALATE` on input would show the escalating secret to
  the approver. Named in spec §9 decision 5 and in the README, with `DENY` recommended instead and
  `include-arguments` available as the other way out. Not a defect of this module, but an
  interaction that had to be written down somewhere.

## Verdict

**APPROVED.** Proceeds to `update-docs`.
