# guardrails-core

Guardrails Core provides the vocabulary every guardrail speaks and the single entry point into
the hexagon: an interceptor that decorates MCP tool-call handlers so each invocation is
evaluated by the guardrail chain before the real tool runs, and its result by the outbound chain
before it reaches the agent.

This module has no runtime state and no auto-configuration — the
[spring-boot-starter](../spring-boot-starter) module wires everything.

## What it provides

* `ToolInvocationContext`, `GuardrailDecision` (`Allow` / `Deny(reason)` / `Escalate(reason)`)
  and `ChainVerdict` — the shared model, pure JDK records.
* `Guardrail` (inbound SPI): implement this to plug your own guardrail into the chain. Give it a
  unique `name()` and optionally an `order()` (lower runs earlier, ties broken by name).
* `GuardrailChain`: evaluates every registered guardrail — no short-circuit, so the verdict
  carries the full decision trace — and combines decisions with severity
  `Deny > Escalate > Allow`. A guardrail that throws is recorded as `Deny` (fail closed).
* `ToolResultContext`, `ResultDecision` (`PassThrough` / `Redact(contents, reason)` /
  `Block(reason)`) and `ResultVerdict` — the outbound model.
* `ResultGuardrail` (outbound SPI): implement this to inspect what a tool *returns*, after it ran
  and before the agent sees it. Same `name()` / `order()` contract as `Guardrail`.
* `ResultGuardrailChain`: evaluates every outbound guardrail with redactions composing in
  cascade, and combines decisions with severity `Block > Redact > PassThrough`. A guardrail that
  throws, returns null, or returns a `Redact` of the wrong size is recorded as `Block` (fail
  closed).
* `GuardrailToolDecorator` / `GuardedToolCallHandler`: wrap any MCP `SyncToolSpecification` so
  `Deny` and `Escalate` return an `isError` MCP result without executing the tool, and so the
  result is rewritten on `Redact` or replaced with an error on `Block`.

## Configuration

| Property | Default | Description |
|---|---|---|
| `mcp.guardrails.enabled` | `true` | Global switch. When `false` the starter does not wrap tool handlers. |

## Extending

Expose any bean implementing
`io.github.tikyparkinson.mcpguardrails.core.application.port.out.Guardrail` and the starter adds
it to the inbound chain automatically. There is no default store to replace here — core is
stateless; persistence-backed guardrails document their own pluggable ports in their READMEs.

The outbound SPI is
`io.github.tikyparkinson.mcpguardrails.core.application.port.out.ResultGuardrail`. It is not
wired by the starter yet: build a `ResultGuardrailChain` with your guardrails and pass it to
`GuardrailToolDecorator.decorate(specification, useCase, resultUseCase, agentIdResolver, clock)`
to activate it. Auto-configuration ships with the first module that consumes it.

With no `ResultGuardrail` registered the outbound chain is a no-op: the result is returned by
identity, exactly as before the SPI existed.

Outbound guardrails see every redactable text of the result — the text of each `TextContent` and
of each `EmbeddedResource` carrying textual contents, so a tool returning a file as a resource is
inspected too — which they may redact positionally, plus its structured content, which is
read-only: a secret found there can only be answered with `Block`.

## License

Apache 2.0 — see the repository [LICENSE](../LICENSE).
