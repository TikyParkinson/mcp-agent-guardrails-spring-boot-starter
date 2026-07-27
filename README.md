# MCP Agent Guardrails

[![Maven Central](https://img.shields.io/maven-central/v/io.github.tikyparkinson/mcp-guardrails-spring-boot-starter.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.tikyparkinson/mcp-guardrails-spring-boot-starter)
[![CI](https://github.com/TikyParkinson/mcp-agent-guardrails-spring-boot-starter/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/TikyParkinson/mcp-agent-guardrails-spring-boot-starter/actions/workflows/ci.yml)
[![CodeQL](https://github.com/TikyParkinson/mcp-agent-guardrails-spring-boot-starter/actions/workflows/codeql.yml/badge.svg)](https://github.com/TikyParkinson/mcp-agent-guardrails-spring-boot-starter/actions/workflows/codeql.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

MCP Agent Guardrails helps you put security and governance controls around
[MCP](https://modelcontextprotocol.io) tool calls in Java / Spring Boot applications with
absolute minimum fuss. Every tool invocation runs through a chain of guardrails — audit,
authorization, prompt-injection detection and rate limiting — before the tool executes.

When you expose MCP tools to LLM agents, the "client" is a model deciding on its own what to
invoke and with which arguments. This project closes the four gaps that opens.

Our primary goals are:

* Work with **zero configuration**: add one dependency and all four guardrails are active with
  sensible in-memory defaults.
* Be opinionated, but get out of the way: every store, policy source and rule set is a port —
  expose your own bean and the default backs off.
* **Fail closed**: a guardrail that throws denies the call rather than passing it. The one
  deliberate exception is recording the decision trace — a broken audit store degrades
  observability, never protection, because a store that could block calls would be a single point
  of failure for the whole server.
* Never persist tool arguments (PII/secret risk) — audit trails carry metadata only.

## Modules

| Module | Description |
|---|---|
| [guardrails-core](guardrails-core) | Shared model, `Guardrail` and `ResultGuardrail` SPIs, inbound and outbound chains, and the MCP tool-call interceptor |
| [guardrails-audit](guardrails-audit) | Audit trail of every invocation, every guardrail decision, both chains and every human approval |
| [guardrails-authz](guardrails-authz) | Declarative agent→tool authorization policy (first-match-wins rules) |
| [guardrails-injection-guard](guardrails-injection-guard) | Rule-based prompt-injection detection over tool arguments |
| [guardrails-ratelimit](guardrails-ratelimit) | Fixed-window rate limiting per (agent, tool) pair |
| [guardrails-tool-integrity](guardrails-tool-integrity) | Trust-on-first-use fingerprint of each tool definition, blocking tool-poisoning rug-pulls |
| [guardrails-credential-leak-guard](guardrails-credential-leak-guard) | Detects credentials in tool arguments and redacts the ones a tool returns |
| [guardrails-egress-control](guardrails-egress-control) | Allowlist of outbound destinations for tools with network capability, empty by default |
| [guardrails-anomaly-detector](guardrails-anomaly-detector) | Escalates an agent whose recent history looks like a loop or a sweep across tools it never used |
| [guardrails-approval-gate](guardrails-approval-gate) | Holds an escalated invocation until a person approves or rejects it; silence denies |
| [guardrails-trifecta-correlator](guardrails-trifecta-correlator) | Escalates a session where private data, untrusted content and outbound communication all meet |
| [spring-boot-starter](spring-boot-starter) | Auto-configuration that assembles everything — the artifact you import |

## Installation and Getting Started

Add the starter — that is all it takes:

```xml
<dependency>
  <groupId>io.github.tikyparkinson</groupId>
  <artifactId>mcp-guardrails-spring-boot-starter</artifactId>
  <version>0.2.0</version>
</dependency>
```

Any `SyncToolSpecification` bean in your MCP server is decorated automatically; you do not change
how you register tools. A denied call returns an `isError` MCP result and the tool never runs.

### Out of the box

With no configuration all eleven modules load and every one of them allows: the guardrails that
need a policy start with an empty one, so upgrading never begins rejecting calls you were already
making. You get the audit trail, the tool-definition baseline and the anomaly history immediately;
the rest start protecting you as you declare what your tools do.

Two things are worth turning on deliberately, because they are what most of the chain reasons
about:

- **which tools reach outside**, for `egress-control`
- **what each tool can do**, for `trifecta-correlator`

### Configuring the modules

Every module has its own prefix and its own `enabled` flag. Note that three prefixes are shorter
than the module name: `egress`, `anomaly` and `trifecta`.

```yaml
mcp:
  guardrails:
    enabled: true                 # master switch; false disables everything

    authz:                        # who may call what
      default-effect: DENY
      rules:
        - { agent: "prod-agent", tool: "*",             effect: ALLOW }
        - { agent: "*",          tool: "drop_database", effect: ESCALATE }

    tool-integrity:               # trust on first use; blocks rug-pulls
      on-mismatch: DENY
      on-unknown-definition: ALLOW

    injection-guard:              # prompt injection in tool arguments
      built-in-rules-enabled: true

    credential-leak:              # secrets in arguments, and in what tools return
      on-confirmed-input: DENY
      on-suspected-input: ESCALATE
      on-output-text: REDACT

    egress:                       # where tools are allowed to send data
      on-violation: DENY
      allowed-destinations: ["api.internal.example.com"]
      tools:
        - { name: "http_post", destination-arguments: ["url"] }

    anomaly:                      # loops and sweeps in recent history
      window: PT1M
      repeat-threshold: 5
      novel-tool-threshold: 3

    trifecta:                     # private data + untrusted content + egress in one session
      session-idle-timeout: PT30M
      session-max-duration: PT2H
      tools:
        - { name: "read_customer", capabilities: [PRIVATE_DATA] }
        - { name: "fetch_page",    capabilities: [UNTRUSTED_CONTENT] }
        - { name: "send_email",    capabilities: [EXTERNAL_COMMS] }

    approval:                     # holds an escalated call until a person decides
      timeout: PT2M
      max-pending: 20

    ratelimit:
      max-invocations: 60
      window: PT1M

    audit:
      in-memory-max-events: 1000
```

Setting a module's `enabled: false` removes its guardrail from the chain and leaves the rest
running. Each module README documents its full property list and its pluggable port.

### How a call is evaluated

Guardrails run in a fixed order and **all of them always evaluate**, so you get the full decision
trace even when the first one already denied:

```
audit → tool-integrity → authz → injection-guard → credential-leak
      → egress-control → anomaly-detector → trifecta-correlator → ratelimit
```

`audit` runs first so a denied call is still recorded; `ratelimit` runs last so a call rejected
earlier does not consume quota. Verdicts combine as `Deny > Escalate > Allow`.

After the tool returns, a second chain inspects the response — today `credential-leak` redacts
secrets the tool put in its own output.

### Escalation needs somewhere to go

An `Escalate` verdict is not a rejection: it is a request for a human decision.
[guardrails-approval-gate](guardrails-approval-gate) holds the invocation until someone approves or
rejects it, and silence denies. It is on the classpath by default, but it needs a channel: the
starter publishes `ResolveApprovalUseCase` as a bean for you to inject into your own controller,
and ships no HTTP endpoint of its own. That endpoint decides who may lift a block, so protect it
like one.

If no `EscalationResolver` is present, an escalation returns an error to the agent — fail-closed,
but indistinguishable from a failure. The starter warns about exactly that at start-up.

## Getting Help

* Check the module READMEs linked above — each documents configuration properties and how to
  replace the default adapter.
* Read [ARCHITECTURE.md](ARCHITECTURE.md), the project's law: hexagonal architecture rules,
  quality gates and the Definition of Done.
* The formal specs live in [docs/specs](docs/specs) — one spec plus one approval record
  (`*-DONE.md`) per module.
* Report bugs at [github.com/TikyParkinson/mcp-agent-guardrails-spring-boot-starter/issues](https://github.com/TikyParkinson/mcp-agent-guardrails-spring-boot-starter/issues).

## Contributing

We welcome contributions of all kinds! Please read our
[contribution guidelines](CONTRIBUTING.md) before submitting a pull request — the project has
a strict quality bar ([ARCHITECTURE.md](ARCHITECTURE.md) is the law). Security issues go
through [SECURITY.md](SECURITY.md), never public issues.

## Reporting Issues

* Before you log a bug, please search the issue tracker to see if someone has already reported
  the problem.
* Please provide as much information as possible: project version, JVM version, MCP SDK version
  and, if possible, a test case that replicates the problem.

## Building from Source

You need JDK 25 and Docker (the audit and ratelimit modules run integration tests against a
real PostgreSQL through Testcontainers).

```shell
$ mvn verify
```

`mvn verify` fails the build unless everything holds: 163 tests, Jacoco coverage ≥ 80% lines
and branches per module (currently 100%/100% on all six), Spotless formatting and Checkstyle.

CI runs the same command on every push and pull request
([ci.yml](.github/workflows/ci.yml)); releases are published to Maven Central from `v*.*.*`
tags ([release.yml](.github/workflows/release.yml)).

## License

MCP Agent Guardrails is Open Source software released under the
[Apache 2.0 license](LICENSE).
