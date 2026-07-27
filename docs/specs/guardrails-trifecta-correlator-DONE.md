# guardrails-trifecta-correlator — APPROVED

**Date:** 2026-07-26
**Reviewed against:** ARCHITECTURE.md §9 checklist
**Spec:** [`guardrails-trifecta-correlator-spec.md`](guardrails-trifecta-correlator-spec.md)
**Prerequisite:** [`guardrails-core-session-metadata-DONE.md`](guardrails-core-session-metadata-DONE.md)

## Coverage

```
Tests run: 123, Failures: 0, Errors: 0, Skipped: 0     BUILD SUCCESS

LINE    100.0%  (176/176)
BRANCH  100.0%  (70/70)
METHOD  100.0%  (51/51)
```

Threshold required by §8 is 80% lines and 80% branches. Full reactor (13 modules):
`BUILD SUCCESS`.

## Checks

| # | Check | Evidence |
|---|---|---|
| 1 | Spec exists, code does not drift from it | All 9 domain types present with the spec's names; the 6 port signatures match term for term. Design decision 8 records the one change made during implementation |
| 2 | No `org.springframework` in `domain`/`application` | `grep` over both packages: **0** results |
| 3 | Dependency versions are verified GA | Spring Boot `4.1.0`, JUnit `6.1.2`, Mockito `5.23.0` — all HTTP 200 on Maven Central. No RC, milestone or unresolved version TODO |
| 4 | Spotless and Checkstyle clean | `0 Checkstyle violations`, `BUILD SUCCESS` |
| 5 | Jacoco ≥80% lines and branches | 100% / 100% (above) |
| 6 | Testcontainers for a real store adapter | **N/A** — the only outbound adapter is in-process. §8 requires it only when a real store exists |
| 7 | Every `<dependency>` justified in spec §7 | 4 declared, 4 listed: `guardrails-core`, `spring-boot` (provided), `junit-jupiter`, `mockito-core`. None added |
| 8 | Apache 2.0 header on every `.java` | 25 of 25 files |
| 9 | README documents the pluggable port | §"Replacing the session store" names `SessionCapabilityPort`, its three methods, the default adapter's real limitation, and the two properties a replacement must honour |
| 10 | No production method over ~25 lines or mixing responsibilities | Automated scan: none |
| 11 | No `return null` in `domain`/`application` | `grep`: **0**. Absence is modelled by `TrifectaIncomplete` and by an empty capability set |

## Verified beyond the checklist

- **ARCHITECTURE.md §5 and §5.2 respected.** No import of any `guardrails-*` module other than
  core, in code or in the pom; the module declares its own `SessionCapabilityPort`.
- **Chain order 90 does not collide.** Full scan of every `order()` in the project: `-100` audit,
  `-50` tool-integrity, `0` authz, `50` injection-guard, `60` credential-leak, `70` egress-control,
  `80` anomaly-detector, **`90` trifecta-correlator**, `100` ratelimit.
- **Sessions of the same client stay apart.** One connection closing the trifecta leaves another
  connection of the same `agentId` on `Allow`. This was the defect the first draft of the spec
  would have shipped: correlating on the agent identifier — the client product's name — would have
  closed the triangle across unrelated people.
- **A busy session expires.** An agent invoking every ten seconds refreshes the idle clock on every
  call and never reaches it; the absolute bound restarts the session just past two hours. Without
  it a trifecta closed in the morning would still escalate the next day.
- **Exactly one invocation can claim the closure.** A hundred concurrent invocations supplying the
  missing leg produce a single `closedNow`, because the merge and the read happen inside one
  `ConcurrentHashMap.compute`.
- **Never denies.** The `switch` over the sealed verdict has two arms, `Allow` and `Escalate`.
  Legitimate sessions do meet all three legs, so denying would break the product rather than
  protect it.
- **Reasons are reproducible.** Legs are named by iterating `Capability.values()`, not the set, so
  two incidents of the same shape read identically.

## Noted, not blocking

- `InMemorySessionCapabilityAdapter` exposes `trackedSessions()` and `clear()`, which spec §5.3
  does not list. Both are adapter-local observability, outside the port contract, following the
  precedent of `InMemoryEgressPolicyAdapter.currentPolicy()` and the other in-memory adapters.
- `TrifectaStartupWarnings` is not in the spec either. It exists because the spec and the README
  both promise the module announces its own inertness, and ARCHITECTURE.md §5.2 requires it; a
  promise with no code behind it would be the exact failure mode that rule describes.
- The module detects nothing until tools are declared. That is deliberate (spec §9 decision 7) and
  announced at start-up, but it means an operator who adds tools over time has a list to maintain,
  and nothing enforces that. Documented in the README's limitations rather than solved.
- Correlation is over capabilities, not data flow: the module cannot tell whether the private data
  a session read is the data it later sent. That is the framework's claim and its limit, and the
  README says so.

## Verdict

**APPROVED.** Proceeds to `update-docs`.
