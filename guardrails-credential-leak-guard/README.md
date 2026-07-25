# guardrails-credential-leak-guard

Guardrails Credential Leak Guard keeps API keys, passwords and tokens out of the loop in both
directions: it stops an invocation whose **arguments** carry a credential, and it redacts the
credentials a tool returns in its **result** before they reach the model's context — where they
would otherwise be free to travel into anything the model writes next.

It is the first module to implement both SPIs of [guardrails-core](../guardrails-core):
`Guardrail` on the way in and `ResultGuardrail` on the way out. No auto-configuration here yet —
the `spring-boot-starter` integration ships separately.

## How it works

- **Inbound** (order `60`, right after `injection-guard`): the arguments are flattened —
  including nested maps and lists — and matched against the active patterns. A `CONFIRMED`
  match denies the call, a `SUSPECTED` one escalates it; both actions are configurable. Clean
  arguments are allowed.
- Arguments are **never rewritten**. A tool receiving data other than what the agent asked for,
  invisibly to both, is worse than a stopped call.
- **Outbound**: every redactable text of the result — the text of each content and of each
  embedded resource, so a tool returning a file is covered too — is rewritten with each detected
  value replaced by `[REDACTED:<patternId>]`, and the verdict is `Redact` (configurable to
  `Block`). A clean result passes through untouched.
- A credential in the result's **structured content** is always `Block`ed, whatever the
  configuration says: the outbound SPI exposes it read-only, so it cannot be handed back
  sanitized, and letting it through would be a fail-open over a confirmed leak.
- Findings never carry the detected value, not even truncated: only `patternId@location`. A
  reason string reaches the model, so writing the secret there would reintroduce the very leak
  this guardrail prevents.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `mcp.guardrails.credential-leak.enabled` | `true` | Registers both guardrails. |
| `mcp.guardrails.credential-leak.built-in-patterns-enabled` | `true` | Includes the eleven built-in patterns. |
| `mcp.guardrails.credential-leak.on-confirmed-input` | `DENY` | `DENY` or `ESCALATE` for an unmistakable credential in the arguments. |
| `mcp.guardrails.credential-leak.on-suspected-input` | `ESCALATE` | `DENY` or `ESCALATE` for a keyword heuristic match in the arguments. |
| `mcp.guardrails.credential-leak.on-output-text` | `REDACT` | `REDACT` or `BLOCK` for a credential in the textual result. |
| `mcp.guardrails.credential-leak.custom-patterns` | empty | Extra patterns: `{ id, regex, severity, secret-group }`. |

The built-in set covers AWS access keys and secret keys, OpenAI keys, GitHub, Slack and Google
tokens, JWTs, PEM private key blocks, connection strings with an inline password, bearer tokens
and generic `password=` / `api_key=` assignments.

Patterns whose regex also matches the key in front of the value declare a `secret-group`, so
redaction removes the value and leaves the key readable: `DB_PASSWORD=hunter2000` becomes
`DB_PASSWORD=[REDACTED:credential-assignment]`, not `DB_[REDACTED:…]`.

## Replacing the pattern source

The port is
`io.github.tikyparkinson.mcpguardrails.credentialleak.application.port.out.SecretPatternSetPort`
(a single `activePatterns()`). The default `InMemorySecretPatternSetAdapter` resolves a fixed
list at startup, so patterns only change on restart — enough for format-based detection, which
is what the built-in set does.

Expose your own bean to detect the **actual values** managed by a secret manager (Vault,
CyberArk, Azure Key Vault) instead of, or in addition to, well-known formats:

```java
@Bean
SecretPatternSetPort managedSecrets(VaultClient vault) {
  return () -> vault.currentSecrets().stream()
      .map(s -> SecretPattern.ofLiteral("vault:" + s.id(), s.value(), SecretSeverity.CONFIRMED))
      .toList();
}
```

`ofLiteral` quotes the value, so regex metacharacters in a password are matched as themselves
instead of widening the pattern, and it is case-sensitive because secrets are.

Three things to weigh before doing this: the real values live in the guardrail's memory —
exactly where this module tries to keep them from being; `activePatterns()` is queried on every
evaluation, so a remote source **must** be cached with a TTL or its latency is added to every
tool call; and scanning cost grows linearly with the number of managed secrets.

## License

Apache 2.0 — see the repository [LICENSE](../LICENSE).
