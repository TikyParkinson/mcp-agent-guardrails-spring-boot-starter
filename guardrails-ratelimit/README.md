# guardrails-ratelimit

Guardrails Rate Limit protects expensive or dangerous tools from runaway agents: fixed-window
rate limiting per `(agent, tool)` pair with a pluggable counter store.

No auto-configuration here — the [spring-boot-starter](../spring-boot-starter) module wires it.

## How it works

* `RateLimitGuardrail` (name `ratelimit`, order `100`) counts every invocation in its fixed
  window. Within the limit ⇒ `Allow`. Exceeded ⇒ `Deny` with count/limit/window in the reason,
  which is what the audit trail records.
* Denied attempts also consume quota — hammering while throttled does not help.
* Fixed-window semantics: at a window boundary a client can burst up to 2× the limit across two
  adjacent instants. Acceptable as a first line of defense; plug a different store/algorithm
  through the port if you need more.
* **The limit is only as strong as the identity it keys on.** With the default `AgentIdResolver`
  the agent id is `clientInfo.name`, which the client picks: renaming itself gives an agent a fresh
  window. Opening a new session does not, since the counter is per agent rather than per
  connection. See [Agent identity](../guardrails-core#agent-identity) in core for how to key on an
  authenticated principal instead.

## Configuration

| Property | Default | Description |
|---|---|---|
| `mcp.guardrails.ratelimit.enabled` | `true` | Registers the guardrail. |
| `mcp.guardrails.ratelimit.max-invocations` | `60` | Allowed invocations per window per (agent, tool). |
| `mcp.guardrails.ratelimit.window` | `PT1M` | Fixed window size (ISO-8601 duration). |

## Replacing the store

The port is
`io.github.tikyparkinson.mcpguardrails.ratelimit.application.port.out.RateLimitStorePort` — a
single atomic `incrementAndCount`. The starter registers `InMemoryRateLimitStoreAdapter`
(per-instance state, lazy eviction of old windows) with `@ConditionalOnMissingBean`.

For shared state across instances use the reference `JdbcRateLimitStoreAdapter` (PostgreSQL
atomic upsert, concurrency-tested with Testcontainers; DDL in
[src/main/resources/mcp-guardrails-ratelimit-schema.sql](src/main/resources/mcp-guardrails-ratelimit-schema.sql))
or expose your own `RateLimitStorePort` bean (e.g. Redis) and the default backs off.

## License

Apache 2.0 — see the repository [LICENSE](../LICENSE).
