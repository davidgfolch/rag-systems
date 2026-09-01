# ADR-0004: SonarQube Static Analysis for Code Quality

- **Status**: Accepted
- **Date**: 2026-09-01

## Context

The monorepo already enforces code quality through manual reviews, architecture rules (`ArchUnit`, `.claude/rules/architecture-guidelines.md`), and JaCoCo coverage thresholds. However, there was no automated static analysis for bugs, vulnerabilities, and code smells, nor a single dashboard for quality, coverage, and duplication trends across modules. Each module's coverage was only measured independently at build time, with no centralized view.

## Decision

Add **SonarQube Community Edition 10.7 LTS** as a local, Docker-based static analysis server, wired into the Maven build via the `sonar-maven-plugin`, and expose it through dedicated wrapper scripts:

- `docker/docker-compose.sonarqube.yml` - local SonarQube container (port `9000`, persistent volumes).
- `pom.xml` - `sonar-maven-plugin` + `sonar.version` in `pluginManagement`.
- `sonar-project.properties` - scanner settings (exclusions, JRE-provisioning skip, report paths).
- `scripts/sonar.{bat,sh}` - `up` / `scan` / `up-scan` / `down` lifecycle; `scan` runs `mvn verify sonar:sonar` (tests + JaCoCo + analysis) with the working CLI parameters.

The **"Clean as You Code" quality gate** (new-code coverage ≥ 80%, no new violations, no new duplication) is the definition of "clean."

## Consequences

### Positive
- Single dashboard for bugs, code smells, coverage, and duplication across all modules.
- New-code quality gate catches regressions from refactors (e.g., coverage drops when a method is split into untested helpers).
- Local-only, no cloud service or CI dependency; fits the local-first learning model (ADR-0003).
- Complements ArchUnit (structure) with static analysis (bugs/smells/coverage).

### Negative
- Requires a running SonarQube container and a generated token to scan.
- The coverage gate measures **new/modified lines**, so refactoring healthy code can temporarily fail the gate until new branches are tested.
- Local Community Edition does not support JRE provisioning, so `sonar.scanner.skipJreProvisioning=true` is mandatory (otherwise scans hit SonarCloud endpoints and fail with HTTP 403).
- Maven-plugin scanner settings must be passed via `-D` flags; the plugin does not reliably read `sonar.host.url`/`sonar.projectKey`/JaCoCo paths from `sonar-project.properties`.

## Reference

- [SonarQube Guide](../../guides/sonarqube.md)
