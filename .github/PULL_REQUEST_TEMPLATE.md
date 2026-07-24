## What does this PR do?

<!-- One-sentence summary. Link the issue: Fixes #123 -->

## Type of change

- [ ] `feat` — new functionality
- [ ] `fix` — bug fix
- [ ] `deps` — dependency bump (GA verified on Maven Central)
- [ ] `docs` / `test` / `refactor` / `build` / `ci` / `chore`

## Checklist

- [ ] Commits follow [docs/COMMIT_CONVENTIONS.md](../docs/COMMIT_CONVENTIONS.md)
- [ ] `mvn verify` passes locally (tests, Jacoco ≥ 80/80, Spotless, Checkstyle)
- [ ] No Spring imports in `domain`/`application` layers (ARCHITECTURE.md §3)
- [ ] Module spec in `docs/specs/` updated if a contract changed
- [ ] Module README updated if configuration properties or ports changed
- [ ] [CHANGELOG.md](../CHANGELOG.md) updated under `[Unreleased]` (for feat/fix/deps/refactor/perf)
