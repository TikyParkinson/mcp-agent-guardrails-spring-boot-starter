# Security Policy

MCP Agent Guardrails is a security project: we take vulnerabilities in the guardrails
themselves especially seriously — a bypass in authz, injection-guard or ratelimit, or a way to
make the chain fail open, is a critical issue.

## Supported Versions

| Version | Supported |
|---|---|
| latest release | ✅ |
| older releases | ❌ — please upgrade |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

Use GitHub's private vulnerability reporting instead:
[Report a vulnerability](https://github.com/TikyParkinson/mcp-agent-guardrails-spring-boot-starter/security/advisories/new).

Include as much of the following as you can:

* Affected module(s) and version.
* A description of the issue and its impact — e.g. guardrail bypass, fail-open behavior,
  audit-trail tampering, PII leakage into the audit log.
* Steps to reproduce, ideally a minimal test case.

## What to Expect

* Acknowledgement of your report within a few days.
* An assessment and, when confirmed, a fix and coordinated disclosure.
* Credit in the release notes if you wish.

## Scope Notes

* The default in-memory adapters are intended for single-instance deployments and development;
  their documented limitations (e.g. per-instance rate limit state) are not vulnerabilities.
* The injection-guard built-in rules are deterministic, best-effort patterns. A phrase that
  slips past them is a rule-improvement request, not a vulnerability — unless it defeats the
  scanning mechanism itself (e.g. the depth cap or argument flattening).
