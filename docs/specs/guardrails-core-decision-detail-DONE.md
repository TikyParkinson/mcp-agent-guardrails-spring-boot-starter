# guardrails-core-decision-detail — APPROVED

**Date:** 2026-07-27
**Reviewed against:** ARCHITECTURE.md §9 checklist and the five conditions of §5.1
**Spec:** [`guardrails-core-decision-detail-spec.md`](guardrails-core-decision-detail-spec.md)

## Coverage

```
guardrails-core: 100.0 % lines (296/296), 100.0 % branches (85/85)
```

## §5.1 conditions

| Condition | Evidence |
|---|---|
| **Additive only** | Two records gain a component, each with a no-arg convenience constructor. The 47 `new Allow()` in the project still compile; `grep` finds **0** `case Allow()` deconstructions, which is the only thing that would break |
| **Neutral when unused** | A guardrail that has no reason to give keeps calling `new Allow()` and behaves identically. Verified by running the chain: an unused reason is `""`, never `null` |
| **Motivated by a real consumer** | `guardrails-audit-full-coverage`. Auditing every `Allow` was pointless without it: `AuthzGuardrail` held its rule id in an audit event, not in the decision, so removing that dependency would have lost `rule[0]` |
| **Its own spec** | This document's spec, run through the agents before its consumer |
| **Not a breaking change** | All 13 reactor modules `BUILD SUCCESS`. Binary compatible too: the `<init>()` descriptor still exists |

## §9 checklist

| # | Check | Evidence |
|---|---|---|
| 1 | Code matches the spec | Both records implemented as written, 5 design decisions recorded |
| 2 | No Spring in `domain`/`application` | 0 matches |
| 3 | GA versions | No new dependencies |
| 4 | Spotless and Checkstyle | 0 violations |
| 5 | Jacoco ≥ 80/80 | 100 % / 100 % |
| 6 | Testcontainers | **N/A** — no outbound store |
| 7 | Dependencies justified | None added |
| 8 | Apache 2.0 header | Both files already had it; no new files |
| 9 | README documents the port | Covered in `guardrails-authz`, the module that supplies the reason |
| 10 | No method over ~25 lines | Both records are 5 lines of body |
| 11 | No `return null` | `reason` is never null; absence is `""` |

## Verified beyond the checklist

- **The reason survives the chain.** Ran a real `GuardrailChain` with three guardrails: the reasons
  arrive intact in each `GuardrailEvaluation`, and stay there when a later `Deny` wins the combine.
  That is exactly what the auditor walks.
- **`DecisionCombiner` needed no change.** It picks one decision, which keeps its own reason; the
  rest stay in their evaluations inside the `ChainVerdict`.
- **The predicted test breakage did not happen.** The spec warned that including `reason` in
  `equals` would break tests comparing against `new Allow()`. 953 tests, 0 failures. The warning
  still stands for anyone writing new ones.

## Verdict

**APPROVED.**
