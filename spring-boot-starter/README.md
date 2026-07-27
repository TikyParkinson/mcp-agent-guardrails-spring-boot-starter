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

### Auditing

The starter is also where auditing happens. ARCHITECTURE.md §5 forbids one guardrail module from
depending on another, so no guardrail writes to the audit bus itself; the wiring layer — which
legitimately depends on all of them — observes each verdict once and records it.

That covers **every** guardrail's decision including the permissive ones, both chains, and how an
escalation ended: approved or rejected by a named person, or closed with no person involved. A
guardrail you contribute yourself is audited by the same mechanism without knowing auditing exists.

Auditing is observation and never a requirement for deciding. Without
[guardrails-audit](../guardrails-audit) on the classpath the chains are published undecorated and
the guardrails still protect the server, and if the audit store fails at runtime the decorators
swallow it: an audit store that could block calls would be a single point of failure for the whole
MCP server.

One allowed invocation records nine events, so the retention of the default in-memory store is
shorter than the raw number suggests — see the [audit README](../guardrails-audit).

## Configuration

Master switch: `mcp.guardrails.enabled=true` (default). Setting it to `false` backs off every
auto-configuration, including the bean post-processor, so no tool is decorated at all.

Backing off means the beans are gone, not inert. If your own code injects a guardrails port, a
mandatory dependency stops the whole application from starting once the switch is off:

```
Parameter 0 of constructor in AuditController required a bean of type
'...AuditLogStorePort' that could not be found
```

Inject through `ObjectProvider` if you want the switch to stay usable:

```java
public AuditController(ObjectProvider<AuditLogStorePort> auditLog) {
  this.auditLog = auditLog;
}

AuditLogStorePort store = auditLog.getIfAvailable();
return store == null ? List.of() : store.findRecent(limit);
```

Turning a single module off (`mcp.guardrails.egress.enabled=false`) removes its guardrail from the
chain but leaves its ports registered, so this only applies to the master switch.

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
