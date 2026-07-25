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
* **Fail closed**: a broken guardrail or audit store never silently lets a call through.
* Never persist tool arguments (PII/secret risk) — audit trails carry metadata only.

## Modules

| Module | Description |
|---|---|
| [guardrails-core](guardrails-core) | Shared model, `Guardrail` and `ResultGuardrail` SPIs, inbound and outbound chains, and the MCP tool-call interceptor |
| [guardrails-audit](guardrails-audit) | Audit trail of tool invocations + the audit bus used by the other guardrails |
| [guardrails-authz](guardrails-authz) | Declarative agent→tool authorization policy (first-match-wins rules) |
| [guardrails-injection-guard](guardrails-injection-guard) | Rule-based prompt-injection detection over tool arguments |
| [guardrails-ratelimit](guardrails-ratelimit) | Fixed-window rate limiting per (agent, tool) pair |
| [guardrails-tool-integrity](guardrails-tool-integrity) | Trust-on-first-use fingerprint of each tool definition, blocking tool-poisoning rug-pulls |
| [guardrails-credential-leak-guard](guardrails-credential-leak-guard) | Detects credentials in tool arguments and redacts the ones a tool returns |
| [spring-boot-starter](spring-boot-starter) | Auto-configuration that assembles everything — the artifact you import |

## Installation and Getting Started

Add the starter — that is all it takes:

```xml
<dependency>
  <groupId>io.github.tikyparkinson</groupId>
  <artifactId>mcp-guardrails-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

Any `SyncToolSpecification` bean in your MCP server is decorated automatically; you do not
change how you register tools. A denied call returns an `isError` MCP result and the tool never
runs. Here is a quick teaser of a locked-down configuration:

```yaml
mcp:
  guardrails:
    authz:
      default-effect: DENY
      rules:
        - { agent: "prod-agent", tool: "*",             effect: ALLOW }
        - { agent: "*",          tool: "drop_database", effect: ESCALATE }
    ratelimit:
      max-invocations: 60
      window: PT1M
```

Decision semantics across the chain: `Deny > Escalate > Allow`, all guardrails always evaluated,
full decision trace kept per invocation. Each module README documents its properties and its
pluggable port.

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
