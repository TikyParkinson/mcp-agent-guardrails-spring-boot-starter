# Contributing to MCP Agent Guardrails

We welcome contributions of all kinds! Please read this guide before submitting a pull
request — this project has an unusually strict quality bar, and knowing the rules up front
saves everyone time.

## Code of Conduct

This project is governed by our [Code of Conduct](CODE_OF_CONDUCT.md). By participating you
are expected to uphold it.

## Before You Start

**Read [ARCHITECTURE.md](ARCHITECTURE.md).** It is the law of the project: if a pull request
contradicts it, the pull request loses. Commits must follow
[docs/COMMIT_CONVENTIONS.md](docs/COMMIT_CONVENTIONS.md) (Conventional Commits) and changes
must be recorded in [CHANGELOG.md](CHANGELOG.md). The short version:

* Every module is an independent hexagon: `domain` and `application` are pure JDK — **zero
  Spring imports, zero I/O** in those layers. Framework code lives in `adapter`/
  `infrastructure` only.
* Each module has a formal spec in [docs/specs](docs/specs). Code must not deviate from the
  spec; if you need to change a contract, update the spec in the same pull request and explain
  the design decision.
* Dependency versions are never written from memory — verify the latest GA on Maven Central
  before pinning anything, and justify every new `<dependency>` in the module's spec.
* No `null` returns in `domain`/`application` (use `Optional` or a sealed type), records for
  value objects, methods ≤ ~25 lines, Apache 2.0 header in every `.java` file.

## Building from Source

You need JDK 25 and Docker (Testcontainers runs real PostgreSQL for the audit and ratelimit
integration tests):

```shell
$ mvn verify
```

The build **fails** unless all of the following hold — run it locally before pushing:

* All tests pass (JUnit 5 + Mockito; Testcontainers for real-store adapters).
* Jacoco coverage ≥ 80% lines **and** branches per module.
* Spotless formatting (`mvn spotless:apply` to fix) and Checkstyle report zero violations.

## Pull Request Checklist

1. One logical change per pull request; keep it small.
2. Tests included: given-when-then style, named `should<Result>When<Condition>`, covering the
   happy path, every decision branch and at least one invalid input.
3. `mvn verify` green locally.
4. Module README updated if you changed configuration properties or a pluggable port.
5. Spec updated if you changed any contract.

## Reporting Bugs

Use the
[issue tracker](https://github.com/TikyParkinson/mcp-agent-guardrails-spring-boot-starter/issues).
Search first; if the issue is new, include the project version, JVM version, MCP SDK version
and — ideally — a failing test case.

For security vulnerabilities, **do not open a public issue** — see [SECURITY.md](SECURITY.md).

## License

By contributing, you agree that your contributions will be licensed under the
[Apache License 2.0](LICENSE).
