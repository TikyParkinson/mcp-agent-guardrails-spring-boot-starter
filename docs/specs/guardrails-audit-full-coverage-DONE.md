# guardrails-audit-full-coverage — APPROVED

**Date:** 2026-07-27
**Branch:** `bug/validation-0.2.0-findings`
**Findings closed:** F-1 and F-1b of the 0.2.0 validation report
**Specs:** [`guardrails-audit-full-coverage-spec.md`](guardrails-audit-full-coverage-spec.md),
[`guardrails-core-decision-detail-spec.md`](guardrails-core-decision-detail-spec.md)

## Coverage

```
953 tests, 0 failures, 0 errors — no exclusions, Testcontainers included
```

| Module | Lines | Branches |
|---|---|---|
| `guardrails-core` | 100.0 % (296/296) | 100.0 % (85/85) |
| `guardrails-audit` | 100.0 % (111/111) | 100.0 % (16/16) |
| `guardrails-authz` | 100.0 % (71/71) | 100.0 % (25/25) |
| `guardrails-injection-guard` | 100.0 % (99/99) | 100.0 % (30/30) |
| `guardrails-ratelimit` | 100.0 % (86/86) | 100.0 % (26/26) |
| `spring-boot-starter` | 100.0 % (184/184) | 100.0 % (45/45) |

Threshold is 80/80.

## §9 checklist

| # | Check | Evidence |
|---|---|---|
| 1 | Code matches the spec | 5 design decisions in the core spec, 10 in the audit one. Decision 10 documents the only observable change in shape |
| 2 | No Spring in `domain`/`application` | 0 matches across the five library modules |
| 3 | GA versions | No RC or milestone. `0.2.0-SNAPSHOT` is the project's own in-development version |
| 4 | Spotless + Checkstyle | BUILD SUCCESS, 0 violations |
| 5 | Jacoco ≥ 80/80 | 100 % lines and branches in all six modules |
| 6 | Testcontainers | `JdbcAuditLogStoreAdapterPostgresTest`, 5 tests against a real PostgreSQL. Verified that the four new enum values fit `event_type VARCHAR(32)` — the longest, `RESULT_PASS_THROUGH`, is 19 characters, and the DDL has no CHECK constraint, so no migration is needed |
| 7 | Dependencies justified | **15 lines removed from poms, 0 added.** The whole change subtracts three Maven dependencies |
| 8 | Apache 2.0 header | 7/7 new files |
| 9 | READMEs | The four affected modules updated, including a `Changed in 0.2.0` notice for consumers who parse `detail` |
| 10 | No method over ~25 lines | 0 |
| 11 | No `return null` in domain | 0 |

## What was closed

**F-1 — architecture violation.** `guardrails-authz`, `guardrails-injection-guard` and
`guardrails-ratelimit` declared `guardrails-audit` as a Maven dependency, which ARCHITECTURE.md §5
forbids without qualification. Verified now that **no `guardrails-*` module depends on any other
except `guardrails-core`**.

**F-1b — five of nine guardrails left no trace.** A call blocked by `credential-leak` used to read
in the log as `authz ALLOW` + `TOOL_INVOKED`, with no record of the block: the trail did not merely
omit, it misled. Auditing now happens once in the wiring layer, which §5 explicitly designates as
the place for a bridge between modules.

Measured on a real context, one allowed invocation went from **2 events to 9**.

## Verified beyond the checklist

- **The audit trail no longer lies.** `FullAuditCoverageTest` asserts that the set of guardrails
  that decide **equals** the set of guardrails audited, read from the running context. If someone
  adds a guardrail that never reaches the trail, that test fails on its own.
- **The secret does not reach the log.** Asserted on both chains. `sanitizedContents` holds the
  response text, and writing it to the audit store would put the content the redaction just removed
  into a store with a different access policy.
- **A broken audit bus does not block anything.** All three decorators swallow their own failures.
  Failing closed would have made the audit store a single point of failure for the whole MCP server.
- **Both approval use cases survive the decoration.** Wrapping the bean instead would have removed
  `ResolveApprovalUseCase` from the context and broken the operator's controller — checked
  explicitly.
- **The extension of core is additive.** 47 `new Allow()` still compile, 0 `case Allow()`
  deconstructions exist, and the `<init>()` descriptor is still there, so it is binary compatible
  too.

## Known behaviour, recorded rather than fixed

`mcp.guardrails.audit.enabled=false` does **not** stop auditing. It removes the `AuditGuardrail`
from the chain — consistent with every other module's flag — but the bus and the store stay, so the
eight decisions are still recorded and only `TOOL_INVOKED` disappears (9 events → 8).

This predates the branch, but the change amplifies it: that flag used to leave three decision
events in place, and now leaves eight. It is documented in a test named after the behaviour, so it
stays a known trait instead of a surprise. Turning auditing off means dropping `guardrails-audit`
from the classpath or using the master switch.

## Verdict

**APPROVED.**

Next: `vulnerability-scanner`, then `update-docs`, then the remaining findings of
the 0.2.0 validation report — F-3 and F-4 first.
