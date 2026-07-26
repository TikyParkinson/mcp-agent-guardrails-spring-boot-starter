# guardrails-approval-gate

The other guardrails answer yes or no. Some of them, though, only mean "this doesn't look right to
me" — `anomaly-detector` escalating an agent that has started looping, `egress-control` configured
to escalate rather than deny. Until now that distinction was nominal: escalating and denying both
returned an error. This module makes it real. When the chain resolves an escalation, the
invocation is **held** and never runs until a person approves or rejects it.

If nobody answers within the deadline, the invocation is denied. Silence never authorizes.

No auto-configuration here yet — the `spring-boot-starter` integration ships separately.

## How it works

- The chain settles on `Escalate`. `guardrails-core` hands the invocation to the registered
  `EscalationResolver`, which is this module. **This is not a guardrail** and has no position in
  the chain: it runs once, after the whole chain has already decided. A guardrail could not do
  this job, since the decision combiner keeps the first escalation it finds and nothing running
  later can lower it back to allow.
- The invocation is published as an approval request — agent, tool, arguments and the reason the
  guardrails gave — and the calling thread waits.
- A person approves or rejects it through whatever channel the operator exposes (below). The first
  decision wins; later ones are refused rather than overwriting it.
- Approved, the tool runs — and its result still goes through the outbound chain. Approving that
  something runs is not approving that its output is seen raw, so a `credential-leak-guard`
  configured to redact still redacts.
- Rejected, expired, or the channel was full: the tool does not run. All three are the same fact,
  and the reason says which one it was.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `mcp.guardrails.approval.enabled` | `true` | Registers the gate. Without it, an escalation returns an error as before. |
| `mcp.guardrails.approval.timeout` | `PT2M` | How long an invocation is held. On expiry it is denied. |
| `mcp.guardrails.approval.max-pending` | `20` | Requests admitted at once. Past it, denied without waiting. |
| `mcp.guardrails.approval.max-pending-per-agent` | `5` | Cap per agent. Must be ≤ `max-pending`. |
| `mcp.guardrails.approval.include-arguments` | `true` | Whether the invocation arguments travel in the request. |

```yaml
mcp:
  guardrails:
    approval:
      timeout: PT2M
      max-pending: 20
      max-pending-per-agent: 5
```

**Two quotas, because they defend against different things.** Every wait holds a server thread, so
the global one keeps the pool from draining. The per-agent one stops a single looping agent — the
very thing `anomaly-detector` escalates — from filling the global quota and leaving every other
agent unable to get anything approved, which would be a denial of service against the approval
mechanism itself.

**Sizing `max-pending`.** What a wait costs depends on the server's threading model, which this
module does not choose. On virtual threads it is negligible: ten thousand concurrent waits measure
around 30 MB. On platform threads — what Tomcat gives you unless
`spring.threads.virtual.enabled=true` — the pool is a couple of hundred, and holding 100 would be
half the server. The default is sized for the worse case; raise it if you run on virtual threads.

**Sizing `timeout`.** Keep it shorter than the MCP client's own timeout. If the client gives up
first, the invocation waits for an approver whose answer can no longer reach anyone.

## Exposing the approval channel

The module brings no transport — no `spring-web`, no messaging — because nothing in this project
does, and tying it to a web stack would limit where it can run. Implement
`ResolveApprovalUseCase` however your operators work. A REST version is about this long:

```java
@RestController
@RequestMapping("/internal/approvals")
class ApprovalController {

  private final ResolveApprovalUseCase approvals;

  ApprovalController(ResolveApprovalUseCase approvals) {
    this.approvals = approvals;
  }

  @GetMapping
  List<ApprovalRequest> pending() {
    return approvals.pendingApprovals();
  }

  @PostMapping("/{id}/approve")
  ResponseEntity<Void> approve(@PathVariable String id, Principal who) {
    boolean done = approvals.resolve(new ApprovalId(id), new Approved(who.getName()));
    return done ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
  }

  @PostMapping("/{id}/reject")
  ResponseEntity<Void> reject(@PathVariable String id, Principal who, @RequestBody String reason) {
    boolean done = approvals.resolve(new ApprovalId(id), new Rejected(who.getName(), reason));
    return done ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
  }
}
```

**That endpoint decides who can authorize a blocked invocation, so it needs protecting like one.**
This module does not authenticate the approver: it records whatever identity you pass, and
`Principal` above is doing the real work. Left open, it is a way to approve anything the guardrails
stopped.

## Replacing the channel

The pluggable contract is
`io.github.tikyparkinson.mcpguardrails.approval.application.port.out.ApprovalRequestPort`:
`submit` publishes a request, `awaitDecision` waits for it, `resolve` records a decision and wakes
the waiter, `pending` lists what is outstanding. The default
`InMemoryApprovalRequestAdapter` implements it; publish your own bean to take over.

Staying on the default means requests live in this process. A restart loses every wait in flight —
nothing is left dangling, since the MCP calls waiting on them die too — and behind a load balancer
a request is only visible on the replica that created it, so your approval channel must reach that
replica. A shared implementation of the port is what fixes both.

If you write one, two properties are not optional. **Never wait while holding a lock**: the wait
lasts as long as the timeout, and serializing it would let one pending invocation block every
other for the full deadline. And **release the request when the wait ends, expired or not**, or it
keeps quota for a caller that already left and shows a person a decision they can no longer
influence.

## What this guardrail cannot do

It cannot pause without holding a thread. The MCP handler this project decorates is synchronous,
so holding an invocation means holding its thread; there is no way around that short of taking the
guardrails to the SDK's asynchronous mode. The quotas above exist because of it.

It does not decide who may approve, and it does not verify that the approver is who they claim.
Both belong to the channel you expose.

And it shows the approver the arguments, which is what makes an informed decision possible and
also what makes the channel sensitive. One combination deserves naming: `credential-leak-guard`
accepts `ESCALATE` on input, so an invocation escalated **precisely because it carries a secret**
would display that secret to the approver — circular, and the opposite of what that guardrail is
for. **Use `DENY` there when this module is active**, or turn off `include-arguments`.

## License

Apache 2.0 — see the repository [LICENSE](../LICENSE).
