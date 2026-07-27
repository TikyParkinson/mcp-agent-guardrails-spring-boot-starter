# guardrails-anomaly-detector — APPROVED

**Date:** 2026-07-26
**Reviewed against:** ARCHITECTURE.md §9 checklist
**Spec:** [`guardrails-anomaly-detector-spec.md`](guardrails-anomaly-detector-spec.md)

## Coverage

```
Tests run: 107, Failures: 0, Errors: 0, Skipped: 0     BUILD SUCCESS

LINE         100.0%  (226/226)
BRANCH       100.0%  (84/84)
INSTRUCTION  100.0%  (1122/1122)
METHOD       100.0%  (49/49)
```

Threshold required by §8 is 80% lines and 80% branches. Full reactor (11 modules): `BUILD SUCCESS`.

## Checks

| # | Check | Evidence |
|---|---|---|
| 1 | Spec exists, code does not drift from it | `docs/specs/guardrails-anomaly-detector-spec.md`, §2–§7 matched class by class |
| 2 | No `org.springframework` in `domain`/`application` | `grep` over both packages: **0** results |
| 3 | Dependency versions are verified GA | Spring Boot `4.1.0`, JUnit `6.1.2`, Mockito `5.23.0` — all HTTP 200 on Maven Central. No RC, milestone or unresolved version TODO |
| 4 | Spotless and Checkstyle clean | `spotless:check checkstyle:check` → `0 Checkstyle violations`, `BUILD SUCCESS` |
| 5 | Jacoco ≥80% lines and branches | 100% / 100% (above) |
| 6 | Testcontainers for a real store adapter | **N/A** — the only outbound adapter is in-process. §8 requires it only when a real store exists |
| 7 | Every `<dependency>` justified in spec §7 | 4 declared, 4 listed in §7: `guardrails-core`, `spring-boot` (provided), `junit-jupiter`, `mockito-core`. None added |
| 8 | Apache 2.0 header on every `.java` | 26 of 26 files |
| 9 | README documents the pluggable port | `README.md` §"Replacing the history" names `InvocationHistoryPort`, both methods, and warns that bridging onto audit silently disables H1 |
| 10 | No production method over ~25 lines or mixing responsibilities | Automated scan: none. Longest is `AgentWindow.read`, 15 lines |
| 11 | No `return null` in `domain`/`application` | `grep`: **0**. Absence is modelled by `NoAnomaly` and `ArgumentsFingerprint.UNKNOWN` |

## Verified beyond the checklist

- **Chain order 80 does not collide.** Full scan of every `order()` in the project: `-100` audit,
  `-50` tool-integrity, `50` injection-guard, `60` credential-leak, `70` egress-control, **`80`
  anomaly-detector**, `100` ratelimit. Matches spec §5.1.
- **ARCHITECTURE.md §5 respected.** No import of any `guardrails-*` module other than core.
- **Never `Deny`.** The `switch` over the sealed `AnomalyVerdict` has two arms only, `Allow` and
  `Escalate`; there is no path that produces a denial. Covered by a test over every `AnomalyKind`.
- **Fail-closed holds.** Design decision 5 claims a failing history port becomes a `Deny` upstream;
  confirmed in `GuardrailChain.safeEvaluate` lines 58–65, which catches `RuntimeException`.
- **No unbounded recursion.** `CanonicalArguments.MAX_DEPTH` caps the recursion, so 100.000 levels
  of hostile nesting do not raise `StackOverflowError` — the failure mode that produced
  `java:S5998` in `egress-control`, and which `safeEvaluate` above would *not* catch, since an
  `Error` is not a `RuntimeException`.
- **Escalation reasons carry no argument values.** Asserted in both `AnomalyGuardrailTest` and
  `ArgumentsFingerprintTest`; the reason travels back to the model.

## Noted, not blocking

- ARCHITECTURE.md described this module as depending on the history of `guardrails-audit` and
  `guardrails-ratelimit`, which §5 forbids and which neither store can actually answer. The
  contradiction was reported rather than worked around; the user resolved it by correcting §6.10
  and adding **§5.2 — Guardrails that need state across invocations**, which generalises the
  approach this module took for `approval-gate` and `trifecta-correlator`.
- `InMemoryInvocationHistoryAdapter` exposes `trackedAgents()` and `clear()`, which spec §5.2 does
  not list. Both are adapter-local observability, outside the port contract, and follow the
  precedent set by `InMemoryEgressPolicyAdapter.currentPolicy()`.
- `CanonicalArguments.of` gained a `requireNonNull` during the test pass: it previously rendered a
  null map as the string `"null"` and produced a valid fingerprint from it. Reported by
  `test-engineer` rather than fixed silently, and covered by a test.
- Two limits of the fingerprint are documented in the class javadoc, in the README and in tests:
  values differing only below `MAX_DEPTH` render alike, and a value with an identity `toString()`
  renders differently per instance. Both are acceptable *only* because the verdict escalates
  rather than blocks — a point worth re-reading if that decision is ever revisited.

## Verdict

**APPROVED.** Proceeds to `update-docs`.
