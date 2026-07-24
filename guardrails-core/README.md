# guardrails-core

Guardrails Core provides the vocabulary every guardrail speaks and the single entry point into
the hexagon: an interceptor that decorates MCP tool-call handlers so each invocation is
evaluated by the guardrail chain before the real tool runs.

This module has no runtime state and no auto-configuration — the
[spring-boot-starter](../spring-boot-starter) module wires everything.

## What it provides

* `ToolInvocationContext`, `GuardrailDecision` (`Allow` / `Deny(reason)` / `Escalate(reason)`)
  and `ChainVerdict` — the shared model, pure JDK records.
* `Guardrail` (SPI): implement this to plug your own guardrail into the chain. Give it a unique
  `name()` and optionally an `order()` (lower runs earlier, ties broken by name).
* `GuardrailChain`: evaluates every registered guardrail — no short-circuit, so the verdict
  carries the full decision trace — and combines decisions with severity
  `Deny > Escalate > Allow`. A guardrail that throws is recorded as `Deny` (fail closed).
* `GuardrailToolDecorator` / `GuardedToolCallHandler`: wrap any MCP `SyncToolSpecification` so
  `Deny` and `Escalate` return an `isError` MCP result without executing the tool.

## Configuration

| Property | Default | Description |
|---|---|---|
| `mcp.guardrails.enabled` | `true` | Global switch. When `false` the starter does not wrap tool handlers. |

## Extending

Expose any bean implementing
`io.github.tikyparkinson.mcpguardrails.core.application.port.out.Guardrail` and the starter
adds it to the chain automatically. There is no default store to replace here — core is
stateless; persistence-backed guardrails document their own pluggable ports in their READMEs.

## License

Apache 2.0 — see the repository [LICENSE](../LICENSE).
