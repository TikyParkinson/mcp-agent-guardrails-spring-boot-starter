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
- **Arguments nobody finished scanning are denied, not allowed.** The walk is bounded — ten
  thousand values and sixty-four levels by default — and running out of budget produces a `Deny`
  rather than an `Allow`. Nothing matching is not the same as nothing being there. The bound is on
  the number of values because that is what costs: a structure nested a thousand levels deep holds
  nine values and scans as fast as one nested eight, while five thousand flat fields is the worst
  case the default admits, at about 3 ms.
- **Map keys are scanned as well as map values.** A credential used as a field name is still a
  credential sitting in the arguments. The location says which it was: `arguments.payload.token`
  for a value, `arguments.payload{token}` for a key.
- **Text that turns out to be Base64 is decoded and scanned too**, reported as
  `arguments.blob(base64)`. A `.env` or a configuration blob serialized into an argument is
  Base64 without anyone trying to fool anyone, and it used to sail past every pattern. Only one
  level is decoded — Base64 inside Base64 is deliberate obfuscation, and unbounded recursion is a
  decompression bomb waiting for someone.
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

## What it does not catch

Pattern matching covers the accident, which is where credentials actually leak from: a tool that
returns a `.env`, an agent that pastes a token into an argument. It does not cover an agent that
sets out to exfiltrate. Verified, and stated here so it is not discovered during an incident:

| Attempt | Result |
|---|---|
| `sk-proj-A1b2…` in the clear | denied |
| The same key Base64-encoded | denied |
| The same key in a map key | denied |
| The same key split by a space | **allowed** |
| The same key reversed | **allowed** |

Splitting and reversing preserve the secret — the receiver can rebuild it — so they are
exfiltration in the same sense Base64 is. The difference is that neither happens by accident, and
catching them would mean testing permutations of every value, at a false-positive rate that would
make the guardrail unusable. Anything encrypted is out of reach of any pattern-based detector at
all.

A Base64 payload over 64 KB is not decoded either. Holding a decoded payload in memory has to be
bounded somewhere, and raising the ceiling only moves the place an attacker sits just outside of.
Closing it properly means decoding as a stream and matching as it goes, which is a redesign out of
proportion to a vector that takes deliberate preparation.

If your threat model includes a deliberately hostile agent, this module is one layer of several,
not the answer: pair it with [egress-control](../guardrails-egress-control) to bound where data can
go and [approval-gate](../guardrails-approval-gate) to put a person in front of the calls that
matter.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `mcp.guardrails.credential-leak.enabled` | `true` | Registers both guardrails. |
| `mcp.guardrails.credential-leak.built-in-patterns-enabled` | `true` | Includes the eleven built-in patterns. |
| `mcp.guardrails.credential-leak.on-confirmed-input` | `DENY` | `DENY` or `ESCALATE` for an unmistakable credential in the arguments. |
| `mcp.guardrails.credential-leak.on-suspected-input` | `ESCALATE` | `DENY` or `ESCALATE` for a keyword heuristic match in the arguments. |
| `mcp.guardrails.credential-leak.on-output-text` | `REDACT` | `REDACT` or `BLOCK` for a credential in the textual result. |
| `mcp.guardrails.credential-leak.custom-patterns` | empty | Extra patterns: `{ id, regex, severity, secret-group }`. |
| `mcp.guardrails.credential-leak.max-scan-nodes` | `10000` | Values examined before the scan gives up. |
| `mcp.guardrails.credential-leak.max-scan-depth` | `64` | Nesting levels explored before the scan gives up. |

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
