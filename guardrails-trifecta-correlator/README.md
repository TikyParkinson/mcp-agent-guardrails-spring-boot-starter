# guardrails-trifecta-correlator

Simon Willison calls it the **lethal trifecta**: an agent is exploitable by design when access to
private data, exposure to untrusted content, and the ability to communicate outwards all come
together. Each on its own is harmless. Together, an instruction hidden inside a piece of data the
agent reads can make it send another piece of data out — no bug required, just the three
capabilities in the same place.

No other guardrail can see this, because each one judges a single invocation and the trifecta is a
property of a session. This module tracks which legs a session has touched and **escalates** once
the three meet — and keeps escalating for the rest of that session, not only on the invocation
that closed the triangle.

**Nothing is detected until you declare your tools** (see Configuration). No auto-configuration
here yet — the `spring-boot-starter` integration ships separately.

## How it works

- The operator declares which legs each tool touches. The module never guesses: a tool's name and
  description are written by whoever publishes the MCP server, which is exactly the vector
  [guardrails-tool-integrity](../guardrails-tool-integrity) exists to catch. A detector whose input
  the attacker controls is not a detector.
- Every invocation adds its tool's legs to the session. Legs are never taken away, which is what
  makes a closed trifecta stay closed: any later invocation sees the three again, even one to a
  completely harmless tool.
- Once the three meet, the verdict is `Escalate` — never `Deny`. Plenty of legitimate sessions meet
  all three: an assistant that reads a ticket, looks up a customer record and replies by email.
  Denying would break the product; escalating puts a person in front of it, and
  [guardrails-approval-gate](../guardrails-approval-gate) turns that into a real pause.
- Runs at order `90`, after `anomaly-detector` (80) and before `ratelimit` (100).
- A tool that was not declared contributes nothing and changes no session.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `mcp.guardrails.trifecta.enabled` | `true` | Registers the guardrail. |
| `mcp.guardrails.trifecta.session-idle-timeout` | `PT30M` | Forget a session after this long without invocations. |
| `mcp.guardrails.trifecta.session-max-duration` | `PT2H` | Forget a session this long after its first invocation, however busy. Must be ≥ the idle timeout. |
| `mcp.guardrails.trifecta.tools` | **empty** | Tools and the legs each one touches. Empty means nothing is ever detected. |

```yaml
mcp:
  guardrails:
    trifecta:
      session-idle-timeout: PT30M
      session-max-duration: PT2H
      tools:
        - name: read_customer_record
          capabilities: [PRIVATE_DATA]
        - name: fetch_url
          capabilities: [UNTRUSTED_CONTENT, EXTERNAL_COMMS]
        - name: send_email
          capabilities: [EXTERNAL_COMMS]
```

Legs are `PRIVATE_DATA`, `UNTRUSTED_CONTENT` and `EXTERNAL_COMMS`. A tool may touch more than one:
`fetch_url` both brings in content nobody on your side wrote and can carry data out in the URL.

**Why two expiries.** The idle bound closes the session of an agent that stopped working. The
absolute bound closes the session of one that never stops — every invocation refreshes the idle
clock, so a busy agent never reaches it. Measured: 8640 invocations over 24 hours, not one expiry.
Without the absolute bound, a trifecta closed in the morning would still be escalating the next
day.

**An empty tool list detects nothing, and the module says so at start-up.** This is the opposite of
[guardrails-egress-control](../guardrails-egress-control), where an empty allowlist denies
everything. The difference is what the emptiness represents: there it is a decision by the
operator, here it is the absence of one. Failing closed on ignorance would make the server unusable
on first boot — but staying silent about it would leave you believing you have a protection you do
not have.

## Sessions

A session is the MCP transport session, taken from the invocation metadata that `guardrails-core`
provides. That matters more than it sounds: the agent identifier is the **client product's name** —
`copilot`, `cursor` — and is shared by everyone using it. Correlating on that would mix unrelated
people: one reads a record, another opens a URL, a third sends an email, and the triangle closes
across three strangers.

When the transport carries no session, the resolver falls back to the agent and **logs a warning at
start-up**. That path is a degradation, not a variant: on it, the mixing above is exactly what
happens. Publish your own `SessionIdResolver` bean if your deployment can identify a conversation
more precisely.

## Replacing the session store

The pluggable contract is
`io.github.tikyparkinson.mcpguardrails.trifecta.application.port.out.SessionCapabilityPort`:
`accumulate` adds an invocation's legs and reports what the session holds afterwards — plus whether
it was already complete, which nothing else can work out — `withTrifecta` lists the closed ones, and
`forget` clears one. The default `InMemorySessionCapabilityAdapter` implements it; publish your own
bean to take over.

Staying on the default means sessions live in this process. A restart loses what was accumulated,
so a half-built trifecta has to be rebuilt. Behind a load balancer each replica sees only its own
share, so **an agent whose calls are spread across replicas can close the triangle without any
single replica seeing it whole** — the case this module exists to catch is also the one sharding
hides. A shared implementation of the port fixes both.

If you write one, two properties are not optional. `accumulate` must be **atomic per session**: read
then write would let two concurrent invocations lose one contribution, and both claim to have closed
the triangle. And expiry must be measured against the **instant passed in**, not a clock of your
own, or the analysis drifts from the rest of the chain.

## Unlocking a session

A closed trifecta escalates every subsequent invocation, and with `approval-gate`'s quotas the
agent jams quickly. That friction is the right behaviour for the framework's most serious
condition, but it needs a way out that is not waiting for expiry. Expose `ResetSessionUseCase`
alongside your approvals controller:

```java
@GetMapping("/internal/trifecta")
List<SessionId> locked() {
  return sessions.lockedSessions();
}

@PostMapping("/internal/trifecta/{id}/reset")
ResponseEntity<Void> reset(@PathVariable String id) {
  return sessions.reset(new SessionId(id))
      ? ResponseEntity.noContent().build()
      : ResponseEntity.notFound().build();
}
```

**Whoever can reset a session can clear the only signal that detects this condition**, so protect
that endpoint exactly like the approval one. Resetting is deliberately manual: approving a single
invocation does not mean the session stopped being dangerous, and wiring one to the other would
turn a routine approval into a blanket permission.

## What this guardrail cannot do

It knows what you told it. A tool you did not declare contributes nothing, so a server whose tools
grow over time will quietly stop covering the new ones — the declaration is a list to maintain, not
a one-off.

It correlates capabilities, not actual data flow. It cannot tell whether the private data a session
read is the data it later sent out; it only says the two abilities met. That is the framework's
claim and its limit: it identifies an exploitable arrangement, not an exploit in progress. Expect
legitimate sessions to trigger it — that is why the verdict escalates rather than blocks.

**Splitting the legs across separate connections evades it.** Verified:

```
session A   read_customer   PRIVATE_DATA        allowed
session B   fetch_page      UNTRUSTED_CONTENT   allowed
session C   http_post       EXTERNAL_COMMS      allowed
locked sessions: []
```

Three connections from the same client, one leg each, no detection. Within a single session the
correlation is solid — reversing the order still trips it — but the boundary of a session is the
boundary of the guardrail, and that follows from the design rather than from a gap in it:
correlating across sessions means correlating on the agent identifier, which is the client
product's name and shared by everyone using it.

The way to close it is to give the correlator an identity worth grouping by. A `SessionIdResolver`
that returns the same id for the same authenticated user across connections turns three sessions
back into one:

```java
@Bean
SessionIdResolver sessionIdResolver() {
  return context -> SessionId.ofMcpSession(myAuthContext.conversationOf(context));
}
```

That trades false positives for coverage — a user's unrelated work now shares a session — which is
why it is not the default. Decide it deliberately.

## License

Apache 2.0 — see the repository [LICENSE](../LICENSE).
