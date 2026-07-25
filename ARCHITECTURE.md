# ARCHITECTURE.md — mcp-guardrails-spring-boot-starter

This document is the **law of the project**. The 5 agents (`spec-architect`, `domain-builder`,
`adapter-builder`, `test-engineer`, `code-reviewer`) must read it before producing anything and
cannot contradict it. If a spec or a fragment of code clashes with this document, this document
wins.

## 1. Project identity

| Field           | Value                                                                                      |
| --------------- | ------------------------------------------------------------------------------------------ |
| groupId         | `io.github.tikyparkinson`                                                                  |
| Root artifactId | `mcp-guardrails-spring-boot-starter`                                                        |
| Repository      | `https://github.com/TikyParkinson/mcp-agent-guardrails-spring-boot-starter`                 |
| Build tool      | Maven (multi-module)                                                                        |
| Java            | 25 (LTS), `--release 25`, preview features **disabled**                                    |
| License         | Apache License 2.0 (header at the top of every `.java`)                                    |
| Distribution    | GitHub (source) + Maven Central (artifacts, via Central Publisher Portal / OSSRH successor) |

## 2. Golden rule on versions: "always the latest GA"

No agent may write a version number from memory. Before pinning the version of **Spring Boot,
Spring AI, the MCP Java SDK, JUnit 5, Mockito, Testcontainers, Jacoco, Spotless or Checkstyle**,
the agent must verify the latest **GA release (no milestone, no RC, no SNAPSHOT)** against Maven
Central (`search.maven.org`) or the official GitHub repository. If the agent has no way to verify
it at that moment, it must leave it explicitly marked as
`// TODO(version-check): verify latest GA before merging` instead of inventing a number.

## 3. Hexagonal architecture — hard rules

Every guardrail is an independent hexagon. The dependency rule is unidirectional:

```
adapters (in/out)  ──depends on──>  application (use cases, ports)  ──depends on──>  domain
```

- **`domain`**: entities, value objects (always immutable `record`s where applicable), pure
  business rules. **Zero** Spring imports, zero I/O, zero framework annotations. If a domain class
  imports anything other than plain JDK or another class of the domain itself, it is a defect and
  `code-reviewer` must reject it.
- **`application`**: use cases (interactors) that orchestrate the domain, and the **ports**
  (`...Port` interfaces) that define what the use case needs from the outside world
  (`AuditLogStorePort`, `RateLimitStorePort`, etc.). Ports are contracts, not implementations.
- **`adapters/in`**: whatever "enters" the hexagon. This is where the MCP tool-call interceptor
  lives, translating a real tool invocation into a call to the use case.
- **`adapters/out`**: whatever "exits" the hexagon. Concrete implementations of the ports
  (`JdbcAuditLogStoreAdapter`, `InMemoryRateLimitStoreAdapter`, etc.).
- **`infrastructure`**: exclusively Spring Boot configuration: `@AutoConfiguration` classes,
  `@ConfigurationProperties`, `spring.factories` / `AutoConfiguration.imports`. No business logic
  here.

## 4. Persistence: pluggable port/adapter

Every guardrail that needs to persist state (auditing, rate limiting) defines its own `out` port
(e.g. `AuditLogStorePort`). The starter **always** ships a default in-memory implementation
(`@ConditionalOnMissingBean`) so that it works with no configuration, and documents how the end
user replaces that bean with their own adapter (JDBC, Redis, whatever). The project itself only
implements, as a reference tested with Testcontainers, an example JDBC/PostgreSQL adapter — it is
not assumed that every user runs Postgres.

## 5. Maven modules

```
mcp-agent-guardrails-spring-boot-starter/                 (pom, packaging=pom, parent)
├── guardrails-core/                    ✅ done — Guardrail SPI, chain, GuardrailDecision, tool-call interceptor, outbound ResultGuardrail SPI (extensible, see §5.1)
├── guardrails-audit/                   ✅ done — auditing/logging of tool calls
├── guardrails-authz/                   ✅ done — agent→tool authorization
├── guardrails-injection-guard/         ✅ done — anti prompt-injection over arguments
├── guardrails-ratelimit/               ✅ done — rate limiting per (agent, tool)
├── guardrails-tool-integrity/          ✅ done — anti tool-poisoning: SHA-256 (TOFU) baseline of each tool definition
├── guardrails-credential-leak-guard/   ✅ done — detects credentials in arguments and redacts the ones a tool returns
├── guardrails-egress-control/          🚧 new — allowlist of outbound destinations (HTTP, email, messaging) per tool
├── guardrails-anomaly-detector/        🚧 new — detects loops and anomalous repeated invocations, using the audit/ratelimit history
├── guardrails-approval-gate/           🚧 new — implements the actual execution of an `Escalate` decision: pauses the action until human approval
├── guardrails-trifecta-correlator/     🚧 new — correlates, at session level, whether the 3 signals of the "lethal trifecta" are active at once
└── spring-boot-starter/                ✅ done — auto-configuration that assembles everything
```

Every `guardrails-*` module internally follows the `domain / application / adapter-in /
adapter-out` subdivision. `spring-boot-starter` only contains `infrastructure`
(auto-configuration) and depends on the modules above. None of them imports another
`guardrails-*` module as a Maven dependency: if a guardrail needs to know what the others
decided (the `trifecta-correlator` case), it does so by reading the decision trace already
exposed by `guardrails-core` for the invocation in flight, never by importing the other
guardrail's module.

### 5.1 Evolution of `guardrails-core`

`guardrails-core` is the SPI of the project, not a frozen module: new guardrails may require
extension points that do not exist yet (an outbound hook, a session-scoped trace, the real
execution of an `Escalate`). Extending it is legitimate, under these conditions:

- **Additive only.** New types, new ports, new `default` methods. Never change the signature or
  the semantics of an existing SPI type: the guardrails already released must compile and behave
  exactly the same.
- **Neutral when unused.** If nobody registers an implementation of the new extension point, the
  behaviour of the chain must be identical to the previous version.
- **Motivated by a real consumer.** An extension point is only added together with the module
  that needs it, never speculatively.
- **Its own spec.** The extension is specified in `docs/specs/guardrails-core-<extension>-spec.md`
  and goes through the same 5 agents as any module, before the module that consumes it.
- **A breaking change is not an extension.** If a change cannot be additive, it is a major version
  of the whole project and is decided outside this document.

The same applies to `spring-boot-starter` when it has to wire a new extension point.

## 6. Build order

**Released (do not rebuild):** `guardrails-core` → `guardrails-audit` → `guardrails-authz` →
`guardrails-injection-guard` → `guardrails-ratelimit` → `spring-boot-starter` (v0.1.0 already on
Maven Central). These modules are not rebuilt from scratch; `guardrails-core` and
`spring-boot-starter` may still receive additive extensions under §5.1.

**Pending, in this order:**

7. `guardrails-tool-integrity` — ✅ done. No dependency on the new ones, so it went first.
8. `guardrails-credential-leak-guard` — ✅ done. Same level as `injection-guard` (analyses tool
   content). Required the core outbound SPI (§5.1): the original `Guardrail` SPI only saw the
   invocation before the tool ran, so the result could not be inspected or redacted. The
   extension `guardrails-core-outbound-spi` was specified and built first, in the same branch.
9. `guardrails-egress-control` — no new dependencies.
10. `guardrails-anomaly-detector` — depends on historical data already exposed by
    `guardrails-audit` and `guardrails-ratelimit` (both done), which is why it cannot come before
    them even though they are already built.
11. `guardrails-approval-gate` — implements what happens when the chain returns `Escalate`; it
    must exist before the correlator because the correlator invokes it.
12. `guardrails-trifecta-correlator` — the most complex one: reads the trace of `authz`,
    `injection-guard` and `egress-control` for that invocation/session and, if it detects the 3
    conditions active at once, forces an `Escalate` decision that `approval-gate` resolves. It
    goes last because it conceptually depends on 9, 10 and 11 already existing.

A new module is not started until the previous one has gone through all 5 agents
(spec → domain → adapter → test → review) and `code-reviewer` has approved it.

**Branch flow, module by module:** every pending module is built on its own branch
`feature/<module>` created from `develop` (e.g. `feature/guardrails-tool-integrity`). The 5
agents run inside that branch. Only when `code-reviewer` approves and
`docs/specs/<module>-DONE.md` exists is a PR opened towards `develop`. The next branch is not
started until the previous PR is merged.

## 7. Clean code standards (mandatory, no exceptions)

- Methods: at most ~25 lines, one responsibility. If a method needs comments to explain "what it
  does" (not "why"), it must be split.
- No `null` as a return value in domain/application: use `Optional<T>` or model the case with a
  type (`sealed interface` + variants).
- No mutable static state, no hand-rolled singletons (Spring manages the lifecycle).
- No "just in case" dependencies: every `<dependency>` in every `pom.xml` must be justified in the
  module's spec. `code-reviewer` rejects any dependency not listed there.
- Package names: `io.github.tikyparkinson.mcpguardrails.<module>.<layer>` (e.g.
  `io.github.tikyparkinson.mcpguardrails.audit.domain`).
- Java 25: use `record` for value objects, `sealed interface` to model closed variants (e.g. the
  result of a guardrail decision: `Allow`, `Deny(reason)`, `Escalate(reason)`), pattern matching in
  `switch` where it adds clarity. Do not use preview features.

## 8. Quality and build

- **Spotless** (automatic formatting, `google-java-format` or equivalent) + **Checkstyle**
  (validation: no wildcard imports, import order, maximum cyclomatic complexity, line length). Both
  run in `mvn verify` and **break the build** on failure.
- **Jacoco**: minimum 80% line and branch coverage per module, enforced as a `check` that fails the
  build when unmet.
- **Testcontainers**: mandatory to test any `out` adapter talking to a real store (e.g. the
  reference JDBC/Postgres adapter). Domain and application tests use plain JUnit 5 + Mockito, with
  no containers.
- CI: GitHub Actions, one workflow running `mvn -B verify` on every push/PR, plus a release job
  publishing to Maven Central on `v*.*.*` tags.

## 9. Definition of Done per module

A `guardrails-*` module is finished only if `code-reviewer` confirms **all** of these points:

- [ ] The spec in `docs/specs/<module>-spec.md` exists and the code does not deviate from it
- [ ] Zero Spring imports in `domain`
- [ ] Every dependency version is a verified current GA, none invented
- [ ] Spotless + Checkstyle pass with no warnings
- [ ] Jacoco coverage ≥ 80% lines and branches
- [ ] `out` adapters backed by a real store are covered by a Testcontainers test
- [ ] No unjustified dependencies in the `pom.xml`
- [ ] Apache 2.0 license header present in every `.java` file
- [ ] The module README explains the pluggable port and how to replace the default adapter
