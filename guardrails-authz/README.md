# guardrails-authz

Guardrails Authz adds declarative agent→tool authorization to MCP servers: ordered rules with
`ALLOW` / `DENY` / `ESCALATE` effects, evaluated first-match-wins.

No auto-configuration here — the [spring-boot-starter](../spring-boot-starter) module wires it.

## How it works

* `AuthzGuardrail` (name `authz`, order `0`) evaluates the policy for every invocation, records
  the matching rule in the decision itself — an `Allow` carries its rule id, so the audit trail
  records why an invocation was permitted and not only that it was — and returns
  `Allow` / `Deny(reason)` / `Escalate(reason)`.
* Policy semantics: **first matching rule wins**; no match ⇒ `default-effect`. Patterns are
  exact (case-sensitive) or the full wildcard `*` — nothing in between, on purpose: partial
  globs invite security mistakes that are hard to audit.
* This module authorizes the declared identity; it does not authenticate (the `AgentId` comes
  from the MCP client info resolved by core).

## Configuration

```yaml
mcp:
  guardrails:
    authz:
      enabled: true            # default
      default-effect: ALLOW    # default — set DENY for a default-deny posture
      rules:
        - { agent: "*",          tool: "delete_database", effect: ESCALATE }
        - { agent: "prod-agent", tool: "*",               effect: ALLOW }
        - { agent: "*",          tool: "*",               effect: DENY }
```

## Replacing the policy source

The port is
`io.github.tikyparkinson.mcpguardrails.authz.application.port.out.AccessPolicyPort`
(`currentPolicy()`, queried on every invocation). The starter registers
`InMemoryAccessPolicyAdapter` built from the properties above with `@ConditionalOnMissingBean`.
For dynamic policies (database, OPA, control plane), expose your own `AccessPolicyPort` bean
and the default backs off.

## License

Apache 2.0 — see the repository [LICENSE](../LICENSE).
