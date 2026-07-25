# guardrails-tool-integrity

Guardrails Tool Integrity defends against **tool poisoning / definition rug-pulls**: an
attacker (or a compromised tool library update) changes a tool's description or metadata after
the operator already trusts it, so the model reads and obeys the poisoned definition.

No auto-configuration here yet — the `spring-boot-starter` integration ships separately.

## How it works

- **TOFU (trust on first use)**: the first verified invocation stores a SHA-256 fingerprint of
  the tool's public definition (name, title, description, schemas, annotations) as the trusted
  baseline. Every later invocation re-verifies the current definition against it.
- Verdicts: baseline established or match ⇒ `Allow`; drift without approval ⇒ **`Deny`** by
  default (configurable to `Escalate`). Runs at order `-50` — trusting the tool precedes any
  decision about the agent.
- **Explicit approval flow**: a `Mismatch` reports the new fingerprint; approve exactly that
  fingerprint through `ApproveToolChangeUseCase.approve(toolName, fingerprint)` — what was
  reviewed is what gets trusted.
- Fingerprints are computed over a canonical form with explicitly sorted map keys, so they are
  reproducible across JVM restarts.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `mcp.guardrails.tool-integrity.enabled` | `true` | Registers the guardrail. |
| `mcp.guardrails.tool-integrity.on-mismatch` | `DENY` | `DENY` or `ESCALATE` when a definition drifts from its baseline. |
| `mcp.guardrails.tool-integrity.on-unknown-definition` | `ALLOW` | `ALLOW`, `DENY` or `ESCALATE` when the tool has no registered definition. |

## Pluggable store

The port is
`io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port.out.ToolBaselineStorePort`
(`find` / atomic `establishIfAbsent` / `replace`). The in-memory default
(`InMemoryToolBaselineStoreAdapter`) loses baselines on restart — fine for development, but
rug-pull attacks live *across* deployments: for real protection use the reference
`JdbcToolBaselineStoreAdapter` (PostgreSQL, tested with Testcontainers; DDL in
[src/main/resources/mcp-guardrails-tool-integrity-schema.sql](src/main/resources/mcp-guardrails-tool-integrity-schema.sql))
or expose your own persistent `ToolBaselineStorePort` bean.

Current definitions reach the guardrail through `ToolDefinitionCatalogPort`; the in-memory
catalog is populated at startup via `InMemoryToolDefinitionCatalog.register(...)` using
`McpToolDefinitionMapper.from(tool)`.

## License

Apache 2.0 — see the repository [LICENSE](../LICENSE).
