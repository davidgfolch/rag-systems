# CI Workflow and Badges

This guide covers the GitHub Actions CI pipeline for the RAG Systems monorepo and how the module status badges in the README are generated.

## Overview

Continuous integration runs on every **push to `main`**, every **pull request targeting `main`**, and on manual dispatch. The pipeline compiles all modules, enforces the architecture rules with ArchUnit, and runs each module's unit tests with coverage.

The workflow lives at [.github/workflows/ci.yml](../../.github/workflows/ci.yml).

## Non-code changes skip the code jobs

A change is *code* only when it touches something that can affect the build, tests, or coverage, mirroring [Rule 1 of the ASDLC](../process/asdlc.md):

- `*.java`
- `pom.xml`
- `mvnw` / `mvnw.cmd`
- `sonar-project.properties`
- `.mvn/**`

Everything else — `docs/`, any `*.md` (including `README.md`), `.claude/`, `.opencode/`, `scripts/*.sh|bat|ps1`, `docker/**`, `.github/workflows/**` — is **non-code**. For those changes the `Detect changes` job reports `code=false` and `build`, `architecture-tests`, `tests` and `coverage-badges` are skipped; only `Detect changes` and `CI gate` run (a few seconds).

The workflow uses a dedicated job instead of `paths-ignore` on purpose: a docs-only pull request still reports a check, so `dev auto-merge` never sees "no checks reported".

## Workflow Jobs

| Job | Purpose | Command |
|-----|---------|---------|
| `changes` | Decides whether code jobs must run | `git diff` against the push range / PR base |
| `build` | Compiles and installs all modules, then caches the `com.rag` artifacts for the matrix | `./mvnw -B -DskipTests install` |
| `architecture-tests` | Runs every ArchUnit suite, including the cross-module file-length gate | `./mvnw -B test -Dtest='*ArchitectureTest' -Dsurefire.failIfNoSpecifiedTests=false` |
| `tests` | Runs each module's tests with JaCoCo coverage (≥ 90% gate) | `./mvnw -B -pl apps/<module> verify` |
| `ci-ok` | Single aggregate status check required by branch protection | fails when any job failed or was cancelled |

The `tests` job uses a **matrix strategy** with `fail-fast: false` so one failing module does not cancel the others. Each module produces a coverage report uploaded as an artifact.

The `build` job installs the reactor once and saves `~/.m2/repository/com/rag` under `rag-artifacts-<sha>`; each matrix job restores that cache and runs only its own module. On a cache miss the matrix job falls back to `-am` (build the module's upstream dependencies in-reactor).

### Modules in the Matrix

The matrix is defined in `.github/workflows/ci.yml`:

```yaml
matrix:
  module:
    - rag-common-core
    - rag-common-ingestion
    - rag-common-retrieval
    - rag-common-generation
    - rag-basic
    - rag-memory
    - rag-webcrawler
    - rag-advanced
    - rag-agentic
    - rag-evaluation
    - rag-observability
    - rag-cli
    - rag-tui
```

> **Adding a new module:** append its name to the `matrix.module` list and add it to `scripts/coverage-badges.sh` / `.bat` / `.ps1`.

## Concurrency

```yaml
concurrency:
  group: ci-${{ github.workflow }}-${{ github.head_ref || github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}
```

- **Pull requests:** a new push cancels the previous run of the same PR (only the latest commit matters).
- **`main`:** runs **queue** instead of cancelling each other, so every merged commit is fully validated. (Cancelling `main` runs used to leave merged commits unverified.)

## Required Check and Branch Protection

`main` is protected by a repository ruleset applied with:

```bash
./scripts/protect-main.sh          # Linux/Mac
.\scripts\protect-main.ps1         # Windows
```

The ruleset:

- requires a pull request before merging;
- requires the **`CI gate`** status check (`ci-ok`);
- blocks force-pushes and branch deletion;
- enables repository auto-merge, which `dev auto-merge` relies on.

Apply the ruleset **after** the CI workflow that defines `CI gate` is on `main`, otherwise pull requests wait for a check that does not exist.

## Badges

The README shows the overall CI badge plus the per-module coverage badges:

```markdown
[![CI](https://github.com/davidgfolch/rag-systems/actions/workflows/ci.yml/badge.svg)](https://github.com/davidgfolch/rag-systems/actions/workflows/ci.yml)
```

- **Overall badge**: points at `workflows/ci.yml/badge.svg`.
- **Coverage badges**: generated from the per-module JaCoCo CSVs by `scripts/coverage-badges.sh` (`.bat` / `.ps1` on Windows) and refreshed locally before a release.

CI no longer commits badge updates to `main`: a repository ruleset on a personal
repository cannot grant the GitHub Actions integration a bypass, and events
created with `GITHUB_TOKEN` do not trigger further workflow runs. Run
`bash scripts/coverage-badges.sh` locally when the coverage values change.

### Badge State

A badge is **green** when the run passes and **red** when it fails or is cancelled. The first CI run on a branch that has never executed the workflow may render "no status" until the workflow has run at least once on a branch.

## Local Equivalents

The CI commands mirror the local scripts:

| CI | Local |
|----|-------|
| `./mvnw test -Dtest='*ArchitectureTest'` | `scripts\test.bat` (architecture suites run with the full suite) |
| `./mvnw -pl apps/<module> verify` | `scripts\test.bat <module> --coverage` |
| `./mvnw -DskipTests install` | `scripts\build.bat` |

Run the same checks locally before pushing so CI stays green.

## Local Developer Workflow

For isolated, parallel feature development use the `dev` scripts
(`scripts\dev.bat` on Windows, `scripts/dev.sh` on Linux/Mac). They clone the
repo into a per-feature sibling folder, create a `feat/<name>` branch,
propagate the auto-generated SonarQube token, and drive the full lifecycle:

| `dev` command | Purpose | CI equivalent |
|---------------|---------|---------------|
| `dev new <name>` | Clone to `../rag-systems-<name>`, branch `feat/<name>` | — |
| `dev test [module]` | Run tests | `tests` job |
| `dev check [module]` | Tests + SonarQube scan | `tests` + quality gate |
| `dev commit` / `dev push` | Stage all, commit, push branch | — |
| `dev pr [title]` | Open PR vs `main` | — |
| `dev merge` | Enable auto-merge, wait for `CI gate`, delete clone | `ci-ok` |
| `dev cleanup <name\|path>` | Delete a sibling clone by name or path | — |

`dev merge` refuses to merge when any check on the pull request is failing or
cancelled, and uses `gh pr merge --auto` so GitHub itself waits for the
required `CI gate` check.

Module-taking scripts (`dev test`/`dev check`, `test`, `run`, `build`, `install`)
validate the module name and print the valid list on error; pass `--list` to
print it on demand (`dev test --list`, `run --list`). `run --list` shows only
runnable Spring Boot modules.

Each feature lives in its own clone so several can run in parallel. The
SonarQube token is copied from the original workspace into each clone on
`dev new`, so scanning always works without manual setup. Merge deletes the
clone automatically (use `dev merge -k` to keep it).
