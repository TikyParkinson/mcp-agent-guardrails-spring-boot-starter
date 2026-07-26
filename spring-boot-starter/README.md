# mcp-guardrails-spring-boot-starter

The artifact end users import. It auto-configures all eleven guardrail modules and decorates every
MCP `SyncToolSpecification` bean, so tool calls run behind the chain without changing how you
register tools. **Zero configuration needed.**

## Installation

```xml
<dependency>
  <groupId>io.github.tikyparkinson</groupId>
  <artifactId>mcp-guardrails-spring-boot-starter</artifactId>
  <version>${version}</version>
</dependency>
```

That is all: any `SyncToolSpecification` bean (single or `List`) is decorated automatically via a
`BeanPostProcessor`.

## How it works

Each invocation crosses two chains.

**Argument chain** — runs before the tool, on what the agent asked for. The first `Deny` stops the
call; an `Escalate` is preserved and handed to the escalation resolver. Order comes from each
guardrail's `order()`, ties broken by `name()`:

| Order | Guardrail | Module |
|---|---|---|
| -100 | `audit` | [guardrails-audit](../guardrails-audit) |
| -50 | `tool-integrity` | [guardrails-tool-integrity](../guardrails-tool-integrity) |
| 0 | `authz` | [guardrails-authz](../guardrails-authz) |
| 50 | `injection-guard` | [guardrails-injection-guard](../guardrails-injection-guard) |
| 60 | `credential-leak` | [guardrails-credential-leak-guard](../guardrails-credential-leak-guard) |
| 70 | `egress-control` | [guardrails-egress-control](../guardrails-egress-control) |
| 80 | `anomaly-detector` | [guardrails-anomaly-detector](../guardrails-anomaly-detector) |
| 90 | `trifecta-correlator` | [guardrails-trifecta-correlator](../guardrails-trifecta-correlator) |
| 100 | `ratelimit` | [guardrails-ratelimit](../guardrails-ratelimit) |

`audit` runs first so a denied call is still recorded; `ratelimit` runs last so a call rejected
earlier does not consume quota.

**Result chain** — runs after the tool, on what it returned. Only
[guardrails-credential-leak-guard](../guardrails-credential-leak-guard) contributes here today: it
redacts secrets the tool put in its response.

Two modules take part without being guardrails.
[guardrails-core](../guardrails-core) provides the chain, the decorator and the bean
post-processor. [guardrails-approval-gate](../guardrails-approval-gate) provides the
`EscalationResolver` that an `Escalate` verdict reaches; without it on the classpath an escalation
returns an error to the agent, and the starter says so at start-up.

## Configuration

Master switch: `mcp.guardrails.enabled=true` (default). Setting it to `false` backs off every
auto-configuration, including the bean post-processor, so no tool is decorated at all.

Each module keeps its own prefix and its own `enabled` flag. Note that three prefixes are shorter
than the module name:

| Prefix | Module |
|---|---|
| `mcp.guardrails` | [core](../guardrails-core) |
| `mcp.guardrails.audit` | [audit](../guardrails-audit) |
| `mcp.guardrails.tool-integrity` | [tool-integrity](../guardrails-tool-integrity) |
| `mcp.guardrails.authz` | [authz](../guardrails-authz) |
| `mcp.guardrails.injection-guard` | [injection-guard](../guardrails-injection-guard) |
| `mcp.guardrails.credential-leak` | [credential-leak-guard](../guardrails-credential-leak-guard) |
| `mcp.guardrails.egress` | [egress-control](../guardrails-egress-control) |
| `mcp.guardrails.anomaly` | [anomaly-detector](../guardrails-anomaly-detector) |
| `mcp.guardrails.trifecta` | [trifecta-correlator](../guardrails-trifecta-correlator) |
| `mcp.guardrails.approval` | [approval-gate](../guardrails-approval-gate) |
| `mcp.guardrails.ratelimit` | [ratelimit](../guardrails-ratelimit) |

Turning off a single module (`mcp.guardrails.egress.enabled=false`) removes its guardrail from the
chain but leaves the rest running. See each module README for its own properties.

## Replacing the stores

Every bean is `@ConditionalOnMissingBean`. Expose your own bean of the port type and the default
backs off:

| Port | Module |
|---|---|
| `AuditLogStorePort` | audit |
| `ToolBaselineStorePort`, `ToolDefinitionCatalogPort` | tool-integrity |
| `AccessPolicyPort` | authz |
| `InjectionRuleSetPort` | injection-guard |
| `SecretPatternSetPort` | credential-leak-guard |
| `EgressPolicyPort` | egress-control |
| `InvocationHistoryPort` | anomaly-detector |
| `SessionCapabilityPort` | trifecta-correlator |
| `ApprovalRequestPort` | approval-gate |
| `RateLimitStorePort` | ratelimit |

Every default adapter is in-memory and **loses its state on restart**. For audit that means losing
the trail; for rate limiting, tool integrity, anomaly detection and the trifecta correlator it also
means an attacker can reset the window by waiting for a redeploy. Modules that ship a JDBC
alternative document it in their own README.

Core is pluggable too: `Clock`, `AgentIdResolver`, `SessionIdResolver`, `EscalationResolver` and
even the whole `EvaluateToolInvocationUseCase` and `EvaluateToolResultUseCase`. Add extra
guardrails by exposing any bean implementing the `Guardrail` or `ResultGuardrail` SPI; the chain
picks them all up.

Two modules are the exception, and it is deliberate. `RequestApprovalService` and
`AssessTrifectaService` each implement two use-case interfaces over one shared state, so they are
published as a single instance under both types and back off together: supplying your own
`ResolveApprovalUseCase` also withdraws `RequestApprovalUseCase`, and you must provide both.

## The human side

`approval-gate` and `trifecta-correlator` need a person. The starter publishes
`ResolveApprovalUseCase` and `ResetSessionUseCase` as beans for you to inject into your own
controller; it ships **no HTTP endpoint**, because that would tie the starter to a web stack. Those
module READMEs show the minimal controller — and warn that such an endpoint decides who may lift a
block, so it needs protecting like one.

## License

Apache 2.0 — see the repository [LICENSE](../LICENSE).
