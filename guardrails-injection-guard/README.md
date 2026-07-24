# guardrails-injection-guard

Guardrails Injection Guard inspects MCP tool **arguments** for prompt-injection attempts —
"ignore previous instructions", system-prompt exfiltration, jailbreak payloads — using
deterministic regex rules with two severities.

No auto-configuration here — the [spring-boot-starter](../spring-boot-starter) module wires it.

## How it works

* `InjectionGuardrail` (name `injection-guard`, order `50`) flattens every string value in the
  tool arguments (nested maps/lists, depth ≤ 8 against nesting bombs) and applies the active
  rules.
* Verdicts: clean ⇒ `Allow` (nothing audited — no noise); any `MALICIOUS` hit ⇒ `Deny`; only
  `SUSPICIOUS` hits ⇒ `Escalate`. Detections are recorded on the audit bus as
  `ruleId@argumentPath` — argument content is **never** persisted.
* Built-in rules (stable ids): `ignore-previous-instructions`, `reveal-system-prompt`,
  `override-role`, `disregard-safety` (MALICIOUS); `do-anything-now`, `base64-blob`
  (SUSPICIOUS).
* Detection only — it does not sanitize or rewrite arguments, and it does not inspect tool
  responses.

## Configuration

```yaml
mcp:
  guardrails:
    injection-guard:
      enabled: true                 # default
      built-in-rules-enabled: true  # default
      custom-rules:
        - { id: "internal-hostnames", pattern: "corp\\.internal", severity: SUSPICIOUS }
```

## Replacing the rule source

The port is
`io.github.tikyparkinson.mcpguardrails.injectionguard.application.port.out.InjectionRuleSetPort`
(`activeRules()`, queried on every scan). The starter registers
`InMemoryInjectionRuleSetAdapter` (built-ins + custom rules) with `@ConditionalOnMissingBean`.
For dynamic rule feeds or an external classifier, expose your own `InjectionRuleSetPort` bean
and the default backs off.

## License

Apache 2.0 — see the repository [LICENSE](../LICENSE).
