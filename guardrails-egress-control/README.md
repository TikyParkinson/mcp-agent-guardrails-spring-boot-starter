# guardrails-egress-control

Guardrails Egress Control is the answer to the third leg of the lethal trifecta: it does not
matter that the agent read a secret if it has nowhere to send it. Before a tool with network
capability runs — outbound HTTP, email, messaging — this guardrail reads the destination out of
its arguments and checks it against an explicit allowlist. A destination that is not on the list,
or one that cannot be read at all, stops the call.

The allowlist is **empty by default**: with no configuration, no egress passes. No
auto-configuration here yet — the `spring-boot-starter` integration ships separately.

## How it works

- The operator declares which tools can reach the network and in which argument the destination
  travels. A tool that was not declared is allowed: this guardrail has no opinion about it. To
  make that boundary tighter, compose with [guardrails-authz](../guardrails-authz) and
  `default-effect: DENY`, which forces every usable tool to be enumerated.
- For a declared tool, every declared path is resolved — including nested ones like
  `request.endpoint` and lists of recipients — and each value is turned into a host: a URL is
  parsed with `java.net.URI`, an email address contributes its domain, a bare host is taken as
  is.
- Every resolved destination must be on the allowlist. If one is not, the verdict is `Deny`
  (configurable to `Escalate`, never to allow). Runs at order `70`, after `credential-leak` (60)
  and before `ratelimit` (100).
- **An unreadable destination is a violation, not missing data.** A declared egress tool whose
  argument yields no host is exactly what an obfuscated target looks like, so it is denied and
  the reason names the argument that could not be read.
- Reasons cite hosts and argument paths, never the raw argument value — a denied URL may carry a
  token in its query string, and the reason is read by the model. Enumerations are deduplicated
  and cut at five entries, with `and N more` for the rest.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `mcp.guardrails.egress.enabled` | `true` | Registers the guardrail. |
| `mcp.guardrails.egress.on-violation` | `DENY` | `DENY` or `ESCALATE`. There is no `ALLOW`: it would remove the fail-closed guarantee. |
| `mcp.guardrails.egress.allowed-destinations` | **empty** | Hosts or `*.domain` patterns that may be reached. Empty denies every egress. |
| `mcp.guardrails.egress.tools` | empty | Tools with network capability: `{ name, destination-arguments }`. |

```yaml
mcp:
  guardrails:
    egress:
      allowed-destinations: ["api.github.com", "internal.corp", "*.internal.corp"]
      tools:
        - name: http_get
          destination-arguments: [url]
        - name: send_email
          destination-arguments: [to, cc]
```

Matching compares whole domain labels: `*.internal.corp` accepts `a.internal.corp` and
`a.b.internal.corp`, but **not** the apex `internal.corp` — list it separately, as in the example
above — and never `internal.corp.evil.com` or `notinternal.corp`.

Three limits worth knowing before deploying: the allowlist is about **hosts**, so
`api.github.com` also permits `https://api.github.com:8443/anything`; hosts with non-ASCII
characters cannot be parsed and are therefore always denied, which blocks homograph domains but
also legitimate internationalized ones; and an IP written in a non-canonical form
(`http://2130706433/` for `127.0.0.1`) does not match its canonical entry, so it is denied.

## Replacing the policy source

The port is
`io.github.tikyparkinson.mcpguardrails.egress.application.port.out.EgressPolicyPort` (a single
`currentPolicy()`). The default `InMemoryEgressPolicyAdapter` resolves the policy from
configuration once at startup, so changing the allowlist means a restart.

Expose your own bean to drive it from a CMDB, a service catalog or a network API:

```java
@Bean
EgressPolicyPort dynamicEgressPolicy(NetworkCatalog catalog) {
  return () -> new EgressPolicy(
      catalog.egressTools().stream().map(t -> new EgressTool(t.name(), t.paths())).toList(),
      catalog.approvedHosts().stream().map(AllowedDestination::of).toList());
}
```

`currentPolicy()` is queried on every evaluation, which is what lets the allowlist rotate without
a restart; in exchange, an adapter backed by a remote system **must** cache with a TTL, or its
latency is added to every tool call.

## What this guardrail cannot do

It checks the destination a tool **declares** in its arguments, not the traffic it actually
opens. A tool that ignores its `url` parameter and connects somewhere else is invisible from
here: containing that needs network-level control or a sandbox around the tool, which is outside
the reach of a guardrails library. Do not treat this module as a substitute for egress filtering
at the infrastructure layer.

## License

Apache 2.0 — see the repository [LICENSE](../LICENSE).
