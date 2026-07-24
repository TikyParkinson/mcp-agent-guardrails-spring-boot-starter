# guardrails-audit

Guardrails Audit records every MCP tool invocation as an immutable event in a pluggable store,
and provides the **audit bus** the other guardrails use to record their decisions.

No auto-configuration here — the [spring-boot-starter](../spring-boot-starter) module wires it.

## How it works

* `AuditGuardrail` (name `audit`, order `-100`) records a `TOOL_INVOKED` event per invocation
  and always returns `Allow` — it observes, it never blocks by itself. If the store fails, the
  chain fails closed (`Deny`): a guardrail system running without an audit trail is a
  compliance hole.
* `RecordAuditEventUseCase` is the bus: authz, injection-guard and ratelimit call it to record
  `DECISION_ALLOW` / `DECISION_DENY` / `DECISION_ESCALATE` events.
* Tool arguments are **never** persisted (PII/secret risk) — only agent, tool, instant,
  emitter, type and a short detail text.

## Configuration

| Property | Default | Description |
|---|---|---|
| `mcp.guardrails.audit.enabled` | `true` | Registers the audit guardrail. |
| `mcp.guardrails.audit.in-memory-max-events` | `1000` | Capacity of the default in-memory buffer. |

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
