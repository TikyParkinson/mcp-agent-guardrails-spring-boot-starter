# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-07-27

### Added

- **BREAKING** `guardrails`: close the evasion and audit gaps found in 0.2.0 (#13)
- `starter`: wire the remaining seven guardrail modules (#12)
- `trifecta-correlator`: escalate a session where the three legs meet (#11)
- `approval-gate`: hold escalated invocations until a person decides (#10)
- `anomaly-detector`: add anomaly detection guardrail for looping or compromised agents (#9)
- `egress-control`: add outbound destination allowlist guardrail (#8)
- `credential-leak`: add credential leak guard and the core outbound SPI it needs (#7)
- `tool-integrity`: add guardrails-tool-integrity module with TOFU baseline verification (#4)

### Changed

- `tool-integrity`: extract duplicated literals and simplify assertThrows lambdas (Sonar S1192, S5778) (#5)

### Fixed

- `injection-guard`: repair a javadoc reference to a deleted constant

## [0.1.0] - 2026-07-24

### Added

- `guardrails-core`: shared model (`ToolInvocationContext`, `GuardrailDecision` sealed
  hierarchy, `ChainVerdict`), `Guardrail` SPI, `GuardrailChain` (no short-circuit,
  fail-closed, severity `Deny > Escalate > Allow`) and the MCP tool-call interceptor
  (`GuardedToolCallHandler`, `GuardrailToolDecorator`).
- `guardrails-audit`: audit trail of tool invocations plus the audit bus
  (`RecordAuditEventUseCase`) used by all other guardrails. In-memory bounded store by
  default; reference JDBC/PostgreSQL adapter with DDL, tested with Testcontainers. Tool
  arguments are never persisted (PII/secret protection).
- `guardrails-authz`: declarative agent→tool authorization (`PolicyRule` list,
  first-match-wins, `ALLOW`/`DENY`/`ESCALATE`, configurable default effect). Property-based
  policy by default; pluggable via `AccessPolicyPort`.
- `guardrails-injection-guard`: regex-based prompt-injection detection over tool arguments
  with six built-in rules (four MALICIOUS ⇒ deny, two SUSPICIOUS ⇒ escalate), recursive
  argument flattening with depth cap, custom rules via properties, pluggable via
  `InjectionRuleSetPort`.
- `guardrails-ratelimit`: fixed-window rate limiting per (agent, tool). In-memory store with
  lazy eviction by default; reference JDBC/PostgreSQL adapter with atomic upsert
  (`ON CONFLICT ... RETURNING`), concurrency-tested with Testcontainers. Denied attempts
  consume quota.
- `spring-boot-starter` (`mcp-guardrails-spring-boot-starter`): zero-configuration
  auto-configuration for all guardrails, `@ConditionalOnMissingBean` defaults, per-guardrail
  enable flags, and a `BeanPostProcessor` that decorates `SyncToolSpecification` beans
  automatically.
- Quality gates: 163 tests, 100% line/branch Jacoco coverage on all six modules, Spotless
  (google-java-format + Apache 2.0 headers) and Checkstyle enforced on `mvn verify`.
- CI/CD: build & verify on push/PR (`ci.yml`), Maven Central release on `v*.*.*` tags
  (`release.yml`), weekly GA version watch (`version-watch.yml`), CodeQL analysis
  (`codeql.yml`).

### Dependencies

- Java 25 (LTS), Spring Boot 4.1.0, MCP Java SDK 2.0.0, JUnit 6.1.2, Mockito 5.23.0,
  Testcontainers 2.0.5, PostgreSQL driver 42.7.11 (via Boot BOM). All versions verified as
  latest GA on Maven Central at pin time (ARCHITECTURE.md §2).

[Unreleased]: https://github.com/TikyParkinson/mcp-agent-guardrails-spring-boot-starter/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/TikyParkinson/mcp-agent-guardrails-spring-boot-starter/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/TikyParkinson/mcp-agent-guardrails-spring-boot-starter/releases/tag/v0.1.0
