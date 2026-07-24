# Commit Conventions

This project follows [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/).
Every commit message must have this shape:

```
<type>(<scope>): <short imperative summary>

[optional body: what and why, wrapped at 72 chars]

[optional footer(s): BREAKING CHANGE:, Fixes #123, Co-authored-by: ...]
```

## Types

| Type | Use for | Appears in CHANGELOG as |
|---|---|---|
| `feat` | New functionality (new guardrail, new port, new property) | Added |
| `fix` | Bug fixes | Fixed |
| `deps` | Dependency version bumps (must be verified GA — ARCHITECTURE.md §2) | Dependencies |
| `test` | Adding or improving tests only | — |
| `docs` | README, specs, javadoc, this file | — |
| `refactor` | Code change that neither fixes a bug nor adds a feature | Changed |
| `perf` | Performance improvement | Changed |
| `build` | Build system: poms, plugins, profiles | — |
| `ci` | GitHub Actions workflows | — |
| `chore` | Anything else that doesn't touch src or docs | — |

## Scopes

Use the module name without the `guardrails-` prefix, or a cross-cutting scope:

`core` · `audit` · `authz` · `injection-guard` · `ratelimit` · `starter` · `parent` · `release`

Examples:

```
feat(authz): support ESCALATE as default effect
fix(audit): map timestamptz to OffsetDateTime in JDBC adapter
deps(parent): bump Testcontainers BOM to 2.0.6 (GA verified)
test(core): cover Guardrail.order() default contract
ci(release): sign artifacts with GPG best practices
docs(ratelimit): document window-boundary burst trade-off
```

## Rules

1. **Summary in English, imperative mood, ≤ 72 chars**, no trailing period.
2. **One logical change per commit.** If the summary needs "and", split the commit.
3. **Breaking changes**: add `!` after the scope (`feat(core)!: ...`) **and** a
   `BREAKING CHANGE:` footer explaining the migration. These require a major version bump
   once we reach 1.0.0.
4. **`deps` commits** must state in the body that the version was verified as latest GA on
   Maven Central (project rule, ARCHITECTURE.md §2).
5. Reference issues in the footer (`Fixes #123`), not in the summary.
6. Update [CHANGELOG.md](../CHANGELOG.md) under `[Unreleased]` in the same pull request for
   any `feat`, `fix`, `deps`, `refactor` or `perf` commit.

## Releases

Releases are cut by tagging `vMAJOR.MINOR.PATCH` (SemVer). The release workflow sets the Maven
version from the tag and publishes to Maven Central. Before tagging: move the `[Unreleased]`
section of the changelog to the new version with the release date.
