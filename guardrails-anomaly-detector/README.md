# guardrails-anomaly-detector

The other guardrails judge one call at a time: is this tool allowed, is this destination on the
list, does this argument carry a secret. None of them notices an agent that does nothing forbidden
but has clearly stopped behaving like itself — stuck retrying the same call forever, or sweeping
across tools it never touched before. That is what this module watches: the shape of an agent's
recent history rather than the call in front of it.

The verdict is only ever allow or **escalate**, never deny. No auto-configuration here yet — the
`spring-boot-starter` integration ships separately.

## How it works

Every invocation is recorded, then the agent's behaviour over the configured window — one minute
by default — is analysed by two independent heuristics, plain counting rules with no model and no
training:

- **Repetition loop (H1).** The same tool called with the same arguments, `repeat-threshold` times
  inside the window. Arguments are compared through a SHA-256 of their canonical form, never
  stored: this guardrail must not become the leak that `credential-leak-guard` prevents. Records
  whose fingerprint is unavailable are skipped, not counted as identical to one another.
- **Novel tool burst (H2).** `novel-tool-threshold` tools the agent had never used, all inside the
  window — what credential harvesting looks like from the outside. It stays silent until the agent
  has made `baseline-min-invocations` calls, because during the first minute of any agent every
  tool is new.

Both firing produces one escalation naming both signals. Runs at order `80`, after
`egress-control` (70) and before `ratelimit` (100).

**Why never deny.** A legitimate retry with backoff reaches the repetition threshold exactly as a
runaway loop does. Blocking on a threshold heuristic produces incidents nobody can reproduce; an
escalation puts a human in front of the same evidence. This is the difference from
`egress-control`, where a destination off the allowlist breaks an explicit rule the operator wrote.

Note what escalation means today: `GuardedToolCallHandler` returns an error and the tool does not
run, so the call is stopped either way. The choice is not whether to block but how the outcome is
labelled — and, once `guardrails-approval-gate` exists, whether a human can release it.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `mcp.guardrails.anomaly.enabled` | `true` | Registers the guardrail. |
| `mcp.guardrails.anomaly.window` | `PT1M` | How far back each analysis looks. |
| `mcp.guardrails.anomaly.repeat-threshold` | `5` | Identical calls that report a loop. |
| `mcp.guardrails.anomaly.novel-tool-threshold` | `3` | Never-before-seen tools that report a sweep. |
| `mcp.guardrails.anomaly.baseline-min-invocations` | `20` | Calls required before H2 speaks at all. |
| `mcp.guardrails.anomaly.retention` | `PT30M` | How long the default adapter keeps history. Must be ≥ `window`. |
| `mcp.guardrails.anomaly.max-records-per-agent` | `500` | Cap on detailed records per agent. |

```yaml
mcp:
  guardrails:
    anomaly:
      window: PT1M
      repeat-threshold: 5
      novel-tool-threshold: 3
      baseline-min-invocations: 20
```

## Replacing the history

The pluggable contract is
`io.github.tikyparkinson.mcpguardrails.anomaly.application.port.out.InvocationHistoryPort`, with
two methods: `append(InvocationRecord)`, which must be safe under concurrency, and
`historyOf(agentId, windowStart)`, which returns the agent's window and its baseline in a single
read and never returns null. The default `InMemoryInvocationHistoryAdapter` implements it; publish
a bean of your own to take over. The port is the contract, not the class.

Staying on the default means the history lives in this process: it is lost on restart, and behind
a load balancer each replica sees only its own slice of an agent's behaviour. For a single
instance that is enough; for a fleet, an agent can stay under every replica's threshold while
being well over it in aggregate.

Within the default adapter the two kinds of data expire differently. Detailed records — one
fingerprint each — are bounded by time **and** count; the baseline summary by time alone, since
its size is capped by how many tools the server exposes. When the count cap is reached the oldest
records are *folded* into the summary rather than dropped. Dropping them would erase the baseline
exactly as an agent enumerating hundreds of tools overflows the cap, leaving it looking like a
newcomer with nothing to deviate from.

One warning if you bridge this onto the audit log: audit stores no arguments, by design. Every
record would arrive with `ArgumentsFingerprint.UNKNOWN` and **H1 would be silently inactive** —
an operator who believes they have loop detection and does not is worse off than one who knows
they have none. Prefer a hybrid: the in-memory window for H1, where fingerprints exist, and audit
for H2's baseline, which gains history and survives restarts. If you do write the pure bridge, log
a warning at startup rather than degrading in silence.

## What this guardrail cannot do

It sees only the invocations that reach this process. Behind a load balancer each replica holds
its own window, so an agent spreading its calls across replicas raises fewer signals than it
should — a shared `InvocationHistoryPort` is what closes that gap, not a setting.

It also compares arguments through a bounded rendering. Values differing only below eight levels
of nesting fingerprint alike, so a deeply nested paging cursor can look like a repeated call; and
a value whose `toString()` is the inherited identity form renders differently per instance, which
hides a real repetition. JSON-decoded arguments never hit the second case; arbitrary objects
passed straight into the port do. Both are tolerable only because the verdict escalates rather
than blocks — revisit them first if that ever changes.

Above all, these are counting rules over recent behaviour, not proof of an attack. They are meant
to put a human in front of an agent that stopped behaving like itself, and they belong alongside
the guardrails that enforce explicit policy, not instead of them.

## License

Apache 2.0 — see the repository [LICENSE](../LICENSE).
