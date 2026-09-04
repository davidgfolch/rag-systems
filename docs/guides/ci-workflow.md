# CI Workflow and Badges

This guide covers the GitHub Actions CI pipeline for the RAG Systems monorepo and how the module status badges in the README are generated.

## Overview

Continuous integration runs on every **push to `main`** and every **pull request targeting `main`**. The pipeline compiles all modules, enforces the architecture rules with ArchUnit, and runs each module's unit tests with coverage.

The workflow lives at [.github/workflows/ci.yml](../../.github/workflows/ci.yml).

## Workflow Jobs

| Job | Purpose | Command |
|-----|---------|---------|
| `build` | Compiles all modules without running tests (fast failure signal) | `./mvnw clean package -DskipTests -B` |
| `architecture-tests` | Runs ArchUnit layer/dependency/quality rules | `./mvnw test -Dtest=ArchitectureTest -DfailIfNoTests=false -B` |
| `tests` | Runs each module's tests with JaCoCo coverage (≥ 85% gate) | `./mvnw -pl apps/<module> -am verify -B` |

The `tests` job uses a **matrix strategy** with `fail-fast: false` so one failing module does not cancel the others. Each module produces a coverage report uploaded as an artifact.

### Modules in the Matrix

The matrix is defined in `.github/workflows/ci.yml`:

```yaml
matrix:
  module:
    - rag-common
    - rag-basic
    - rag-advanced
    - rag-agentic
    - rag-evaluation
    - rag-observability
    - rag-cli
```

> **Adding a new module:** append its name to the `matrix.module` list. Add the corresponding badge row to the README (see below).

## Badges

The README shows a status badge for the overall CI run and one per module, rendered from shields.io / GitHub Actions badges:

```markdown
[![CI](https://github.com/davidgfolch/rag-systems/actions/workflows/ci.yml/badge.svg)](https://github.com/davidgfolch/rag-systems/actions/workflows/ci.yml)
[![rag-basic](https://github.com/davidgfolch/rag-systems/actions/workflows/ci.yml/badge.svg?job=tests-%28rag-basic%29)](https://github.com/davidgfolch/rag-systems/actions/workflows/ci.yml)
```

- **Overall badge**: points at `workflows/ci.yml/badge.svg`
- **Module badge**: appends `?job=tests-<module>` to target the matrix job for that module
- **Architecture badge**: points at `?job=architecture-tests`

### URL Encoding

Matrix job names rendered in URLs wrap the module in parentheses, which must be percent-encoded:

| Character | Encoded |
|-----------|---------|
| `(` | `%28` |
| `)` | `%29` |

So the job `Tests (rag-basic)` becomes `tests-%28rag-basic%29` in the badge URL.

### Badge State

A badge is **green** when the job passes and **red** when it fails. The first CI run on a branch that has never executed the workflow may render "no status" until the workflow has run at least once on a branch.

> **Note:** with `fail-fast: false`, skip/failure behavior per module is independent. The concurrency group (`ci-${{ github.ref }}`) cancels in-flight runs when a newer commit is pushed to the same branch.

## Local Equivalents

The CI commands mirror the local scripts:

| CI | Local |
|----|-------|
| `./mvnw test` (incl. `ArchitectureTest`) | `scripts\test.bat` |
| `./mvnw -pl apps/<module> -am verify` | `scripts\test.bat <module> --coverage` |
| `./mvnw clean package -DskipTests` | `scripts\build.bat` |

Run the same checks locally before pushing so CI stays green.
