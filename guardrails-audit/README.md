# guardrails-audit

Guardrails Audit records every MCP tool invocation as an immutable event in a pluggable store,
and provides the **audit bus** the other guardrails use to record their decisions.

No auto-configuration here — the [spring-boot-starter](../spring-boot-starter) module wires it.

## How it works

* `AuditGuardrail` (name `audit`, order `-100`) records a `TOOL_INVOKED` event per invocation
  and always returns `Allow` — it observes, it never blocks by itself. If the store fails, the
  chain fails closed (`Deny`): a guardrail system running without an audit trail is a
  compliance hole.
* The decision trace behaves the opposite way on purpose, and the asymmetry is worth knowing.
  `AuditGuardrail` is a guardrail the operator asked for and can remove with
  `mcp.guardrails.audit.enabled=false`, so it denying when it cannot record is a choice they made.
  The trace is recorded by the wiring layer instead, on every invocation, and making that fail
  closed would turn the audit store into a single point of failure for the entire MCP server. So a
  store outage stops `TOOL_INVOKED` and the call with it, while the per-guardrail decisions are
  dropped with a warning.
* `RecordAuditEventUseCase` is the bus. **No guardrail module calls it directly**: ARCHITECTURE.md
  §5 forbids one guardrail from depending on another, so the wiring layer in
  [spring-boot-starter](../spring-boot-starter) observes the chain verdict once and records a
  `DECISION_ALLOW` / `DECISION_DENY` / `DECISION_ESCALATE` per guardrail — including guardrails you
  contribute yourself, which get audited without knowing auditing exists.
* The outbound chain is covered the same way, with `RESULT_PASS_THROUGH` / `RESULT_REDACTED` /
  `RESULT_BLOCKED`. A redaction leaves a trace naming the pattern that matched, never the text it
  removed.
* `APPROVAL_RESOLVED` records how an escalation ended: approved or rejected by a named person, or
  closed with no person involved — an expiry or a saturated queue. Who lifted a block and when is
  the event a governance product most needs, and it belongs in the same trail as the rest.
* The `audit` guardrail is the one exception: its own unconditional `Allow` is not recorded, since
  it would repeat after every `TOOL_INVOKED` and say nothing.
* Tool arguments are **never** persisted (PII/secret risk) — only agent, tool, instant,
  emitter, type and a short detail text.
* Every text field is stripped of control and format characters before it is stored. An agent
  chooses the names of its own arguments and, with the default resolver, its own identifier, and
  both reach the trail inside decision reasons — a newline there would let it forge a complete
  audit entry once the trail is dumped to a plain-text log. Values are sanitized rather than
  rejected on purpose: publishing happens inside decorators that swallow their own failures, so
  rejecting would hand an agent a way to *delete* its own trace instead.

> **Changed in 0.2.0.** `detail` now carries the reason of the decision itself. `authz` is
> unaffected (`rule[0]`, `default`), but `ratelimit` used to write `count=6 limit=5 window=PT1M`
> and now writes the full denial reason, with the same numbers in prose. If you parse `detail`,
> check that first.

## Configuration

| Property | Default | Description |
|---|---|---|
| `mcp.guardrails.audit.enabled` | `true` | Registers the audit guardrail. |
| `mcp.guardrails.audit.in-memory-max-events` | `5000` | Capacity of the default in-memory buffer. |

One allowed invocation records nine events: eight guardrails that decide, plus `TOOL_INVOKED`. So
the default holds roughly 550 invocations, and the buffer drops the oldest **silently**. Anything
past a demo should replace the store rather than raise this number — a trail that truncates without
saying so is worse than one you know is short.

## Replacing the store

The port is
`io.github.tikyparkinson.mcpguardrails.audit.application.port.out.AuditLogStorePort`
(`append` / `findRecent`). The starter registers `InMemoryAuditLogStoreAdapter` with
`@ConditionalOnMissingBean`: expose your own `AuditLogStorePort` bean and it backs off.

A reference `JdbcAuditLogStoreAdapter` (PostgreSQL, tested with Testcontainers) ships with the
module — DDL in [src/main/resources/mcp-guardrails-audit-schema.sql](src/main/resources/mcp-guardrails-audit-schema.sql).
It needs `spring-jdbc` and a JDBC driver on your application's classpath.

## License

Apache 2.0 — see the repository [LICENSE](../LICENSE).
