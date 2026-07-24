# mcp-guardrails-spring-boot-starter

The artifact end users import: auto-configures the four guardrails and decorates every MCP
`SyncToolSpecification` bean so tool calls run behind the chain. **Zero configuration needed.**

## Installation

```xml
<dependency>
  <groupId>io.github.tikyparkinson</groupId>
  <artifactId>mcp-guardrails-spring-boot-starter</artifactId>
  <version>${version}</version>
</dependency>
```

That is all: any `SyncToolSpecification` bean (single or `List`) is decorated automatically via
a `BeanPostProcessor`; you don't change how you register tools.

## Configuration

Global switch: `mcp.guardrails.enabled=true` (default). Each guardrail has its own
`mcp.guardrails.<name>.enabled` flag plus module-specific properties — see the module READMEs:
[core](../guardrails-core) · [audit](../guardrails-audit) · [authz](../guardrails-authz) ·
[injection-guard](../guardrails-injection-guard) · [ratelimit](../guardrails-ratelimit).

## Replacing defaults

Every bean is `@ConditionalOnMissingBean`. Expose your own bean of the port type and the
default backs off: `AuditLogStorePort`, `AccessPolicyPort`, `InjectionRuleSetPort`,
`RateLimitStorePort` — plus `Clock`, `AgentIdResolver` and even the whole
`EvaluateToolInvocationUseCase`. Add extra guardrails by exposing any bean implementing the
`Guardrail` SPI; the chain picks them all up, ordered by `order()` then `name()`.

## License

Apache 2.0 — see the repository [LICENSE](../LICENSE).
