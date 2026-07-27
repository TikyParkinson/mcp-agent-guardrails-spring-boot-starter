# guardrails-injection-guard

Guardrails Injection Guard inspects MCP tool **arguments** for prompt-injection attempts —
"ignore previous instructions", system-prompt exfiltration, jailbreak payloads — using
deterministic regex rules with two severities.

No auto-configuration here — the [spring-boot-starter](../spring-boot-starter) module wires it.

## How it works

* `InjectionGuardrail` (name `injection-guard`, order `50`) flattens every string value in the
  tool arguments, walking nested maps and lists within a bounded budget, and applies the active
  rules.
* Verdicts: clean ⇒ `Allow`; any `MALICIOUS` hit ⇒ `Deny`; only
  `SUSPICIOUS` hits ⇒ `Escalate`. The decision reason names the hits as `ruleId@argumentPath`, so
  what reaches the audit trail identifies the rule and the location — argument content is **never**
  persisted.
* Built-in rules (stable ids): `ignore-previous-instructions`, `reveal-system-prompt`,
  `override-role`, `disregard-safety` (MALICIOUS); `do-anything-now`, `base64-blob`
  (SUSPICIOUS).
* **The built-in rules match a fixed word order.** They tolerate any separator between the words
  they expect, but not extra words in between: `reveal-system-prompt` fires on "show the system
  prompt" and not on "show me the system prompt". Widening them is not free — every word admitted
  between the verb and its object also admits sentences that are not attacks — so the defaults stay
  narrow and precise, and phrasings that matter to your deployment belong in `custom-rules`.
* **The built-in rules are written in English only.** A model understands a hundred languages;
  six regexes cannot. An agent driven in Spanish, French or Japanese is not covered by the
  defaults — use `custom-rules` or your own `InjectionRuleSetPort` for those. This is a stated
  boundary rather than an oversight: a half-translated rule set is worse than one you know is
  English, because it reads as coverage you do not have.
* Text is folded before matching, so a rule written in plain ASCII still fires on an argument
  dressed up to slip past it. `ignore-all-previous-instructions`, `ignore_all…`, `ignore.all…`,
  full-width `ｉｇｎｏｒｅ` and Cyrillic `ｉｇｎｏｒｅ`-lookalikes all hit the same rule. See
  [Look-alike characters](#look-alike-characters).
* **Arguments nobody finished scanning are denied, not allowed.** The walk is bounded — ten
  thousand values and sixty-four levels by default — and running out of budget produces a `Deny`.
  Wrapping a payload in enough layers used to skip the guardrail entirely; now it stops the call.
  The limits are a `ScanBudget` from [guardrails-core](../guardrails-core), shared with
  [guardrails-credential-leak-guard](../guardrails-credential-leak-guard) so both stop reading the
  same arguments at the same point.
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
      max-scan-nodes: 10000        # default; reaching it denies the call
      max-scan-depth: 64           # default; a guard against runaway recursion
```

## Look-alike characters

Matching happens against a folded copy of each value; the argument itself is never modified, and a
finding always names the argument as the agent sent it.

Two techniques, because neither covers the other:

* **NFKC** handles everything Unicode gives a compatibility decomposition to — full-width
  `ｉｇｎｏｒｅ`, mathematical `𝐢gnore`, and the rest of roughly a thousand styled letters.
* **A confusables table** handles Cyrillic and Greek letters drawn identically to Latin ones.
  `і` (U+0456) and `i` are separate letters that merely share a glyph, so NFKC leaves them alone.

The table covers Cyrillic and Greek, which is where the glyph-identical characters are in ordinary
fonts. It is **not** the full Unicode TR39 confusables set: that lives in ICU4J and costs about
13 MB, out of proportion for a module that otherwise weighs what its classes weigh. If evasions
ever appear in Armenian or Cherokee, the right answer is to adopt ICU4J rather than to keep growing
the table by hand.

Word separators are treated the same way: rules join words with whitespace, hyphens, underscores
or dots interchangeably. Text with no separator at all (`ignoreallpreviousinstructions`) is not
matched — finding it would mean searching inside words, which fires on ordinary text.

## Replacing the rule source

The port is
`io.github.tikyparkinson.mcpguardrails.injectionguard.application.port.out.InjectionRuleSetPort`
(`activeRules()`, queried on every scan). The starter registers
`InMemoryInjectionRuleSetAdapter` (built-ins + custom rules) with `@ConditionalOnMissingBean`.
For dynamic rule feeds or an external classifier, expose your own `InjectionRuleSetPort` bean
and the default backs off.

## License

Apache 2.0 — see the repository [LICENSE](../LICENSE).
