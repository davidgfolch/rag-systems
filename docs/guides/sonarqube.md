# SonarQube Static Analysis

This guide covers running SonarQube static analysis on the RAG Systems monorepo, how the analysis is wired into the Maven build, and how to fix findings and enforce the quality gate.

## Overview

SonarQube performs continuous code quality inspection across the monorepo:

- **Bugs & vulnerabilities** - broken or insecure code (e.g., S1659, S5778)
- **Code smells** - maintainability issues (e.g., S3776 cognitive complexity)
- **Test coverage** - reports JaCoCo coverage and enforces the "New Code" quality gate
- **Duplication** - duplicated lines in changed code

Analysis is local-first and runs against a **SonarQube Community Edition 10.7 LTS** container on your machine. No cloud service or credentials are required beyond the SQLite-backed local server.

## Stack

| Component | Role |
|-----------|------|
| SonarQube 10.7 LTS (Community) | Analysis server + dashboard |
| `sonar-maven-plugin` | Runs scanner as part of the Maven build |
| JaCoCo | Test coverage report consumed by SonarQube |
| `scripts/sonar.{bat,sh}` | Lifecycle wrapper (start, scan, stop) |

## Architecture (how analysis flows)

```
mvn verify sonar:sonar
   │
   ├── verify      → compiles + runs all tests, produces JaCoCo + Surefire reports
   └── sonar:sonar → uploads sources + reports to local SonarQube (http://localhost:9000)
                         │
                         ▼
              SonarQube analyzes → issues + quality gate → dashboard
```

The Maven plugin reads the project coordinates from the parent POM (`com.rag:rag-systems`), which determines the **SonarQube project key** (`com.rag:rag-systems`). The supporting properties live in `sonar-project.properties` (root).

## Components

### Docker Compose (`docker/docker-compose.sonarqube.yml`)

Overlay file defining a `sonarqube:10.7.0-community` service on port `9000` with persistent volumes:

- `sonarqube_data` - indexed data (survives container restarts)
- `sonarqube_extensions` - installed plugins
- `sonarqube_logs` - server logs

JVM memory is capped (`-Xmx1g`) so the container runs comfortably on a local machine, and the `nofile` ulimit is raised as required by SonarQube. It is layered on top of the base `docker-compose.yml` (the whole stack is named `rag-systems`).

### Maven plugin (`pom.xml`)

`org.sonarsource.scanner.maven:sonar-maven-plugin` is declared in `pluginManagement` with `sonar.version` `5.5.0.6356`.

### Scanner configuration (`sonar-project.properties`)

Root-level properties used by the scanner:

| Property | Value | Notes |
|----------|-------|-------|
| `sonar.projectKey` | `rag-systems` | Used by non-Maven scans; Maven scan uses `com.rag:rag-systems` from the POM |
| `sonar.exclusions` | `**/target/**` | Excludes build output |
| `sonar.scanner.skipJreProvisioning` | `true` | **Required** for Community Edition (no JRE provisioning; otherwise hits SonarCloud endpoints / 403) |
| `sonar.coverage.jacoco.xmlReportPaths` | `**/target/site/jacoco/jacoco.xml` | JaCoCo report location |
| `sonar.junit.reportPaths` | `**/target/surefire-reports` | Test report location |
| `sonar.host.url` | `http://localhost:9000` | Local server |

> **Note:** The Maven plugin does **not** reliably read `sonar.host.url`, `sonar.projectKey`, or the JaCoCo paths from this file. The wrapper scripts pass the required settings explicitly as `-D` arguments.

## Usage

### 1. Configure secrets (optional but recommended)

The local SonarQube Community server always starts with the default `admin` / `admin` credentials and **forces** a password change on first login. There is no declarative way to pre-set the admin password or disable the reset prompt in Community Edition. Instead, the bootstrap script auto-sets a fixed password **and** generates a reusable analysis token on first boot.

`.env.secrets` is bootstrapped automatically: every script runs `scripts/bootstrap-env.{bat,sh}`, which copies `scripts/.env.secrets.example` to the repo root if it does not exist and generates a random `SONAR_ADMIN_PASSWORD` if it is blank. The file is gitignored. No manual copy is needed:

- To use the auto-generated (random) password, do nothing — it is created and saved for you.
- To set a fixed password, edit the root `.env.secrets` and set `SONAR_ADMIN_PASSWORD` before the first `sonar up`.

```
SONAR_ADMIN_PASSWORD=your-secure-password
```

On first boot the script will fill in `SONAR_TOKEN` automatically — do **not** edit it manually.

### 2. Start SonarQube (auto-provisions admin password + token)

```bash
# Windows
.\scripts\sonar.bat up

# Linux/Mac
./scripts/sonar.sh up
```

The script waits for the server to be ready, then runs `scripts/sonar-pw.{bat,sh}`, which:
1. Sets the admin password to `SONAR_ADMIN_PASSWORD` (from `.env.secrets` or the shell, default `admin`).
2. Generates a `rag-local-ci` analysis token and saves it to `SONAR_TOKEN` in `.env.secrets`.
3. Writes a `.sonarqube/admin_pw_set` marker so it only runs once (uses the persistent `sonarqube_data` volume).

Subsequent `up`/`up-scan` runs skip bootstrap. To force a reset (new password/token), delete the `.sonarqube/admin_pw_set` marker and restart (the `sonarqube_data` volume is retained):

```bash
rm .sonarqube/admin_pw_set
.\scripts\sonar.bat up    # Windows
./scripts/sonar.sh up     # Linux/Mac
```

Alternatively start just SonarQube via Docker directly (`docker.sh/bat up-sonar`); you'll need to run the bootstrap or set up a token manually.

> **Note:** SonarQube Community Edition does **not** support disabling the "Forgot password" / password reset flow in the UI. Setting a known fixed password is the supported local equivalent.

### 3. Run analysis

The token is now read automatically from `.env.secrets`, so no token argument is needed after the first bootstrap. Analysis **always runs the full test suite first** (`mvn verify`), then the scanner:

```bash
# Windows (token auto-loaded from .env.secrets)
.\scripts\sonar.bat scan

# Linux/Mac
./scripts/sonar.sh scan
```

You can still override the token explicitly as an argument or via `SONAR_TOKEN`:

Or start the server, wait until it is ready, bootstrap password + token, and scan, in one step:

```bash
.\scripts\sonar.bat up-scan    # Windows (token auto-loaded)
./scripts/sonar.sh up-scan     # Linux/Mac
```

### 3b. Results are exported to the README

After a successful scan, both scripts fetch the quality gate status and key metrics from the SonarQube API (via `scripts/sonar-export.ps1` on Windows, `scripts/sonar-export.sh` on Linux/Mac) and update the **SonarQube → Latest Results** section in `README.md`:

| Metric | Value |
|--------|-------|
| Quality Gate | `OK` |
| Bugs / Vulnerabilities / Security Hotspots | Counts |
| Code Smells | Count |
| Coverage / Duplication | Percentages |

If `jq` (Linux/Mac) is missing or the analysis is not yet available on the server, the export script warns and leaves the README unchanged. The `<!-- SONARQUBE_RESULTS_START/END -->` markers in `README.md` define the auto-updated block — keep them intact.

### 4. Review results

- **Dashboard**: `http://localhost:9000` → project `com.rag:rag-systems`
- **Quality gate**: shown in the dashboard header (``OK`` / ``ERROR``)
- **CI check**: `exit code` is `1` only on build failure, not on a red quality gate (the Maven plugin does not fail the build on gate violations by default; check the dashboard for gate status)

### 5. Stop SonarQube

```bash
.\scripts\sonar.bat down    # Windows
./scripts/sonar.sh down     # Linux/Mac
```

## The Quality Gate

SonarQube's default "Clean as You Code" quality gate evaluates **new code** only:

| Condition | Requirement |
|-----------|-------------|
| `new_coverage` | ≥ 80% on new code |
| `new_duplicated_lines_density` | < 3% |
| `new_violations` | 0 (no new issues, unless security hotspot) |

Because the gate measures **new/modified lines**, refactoring a method (e.g., splitting a large method into helpers) counts the refactored lines as "new code" and can lower `new_coverage` until those branches are covered by tests.

## Fixing Findings (workflow)

1. Run `.\scripts\sonar.bat scan <token>`.
2. Open the dashboard and read the flagged rules (click a rule for an explanation and examples).
3. Apply the minimal code change (e.g., split a multi-declaration, extract a helper, remove dead code, add a test for an uncovered branch).
4. Re-run the scan, ideally with `clean` first so stale `target/` classes never mask a fix:

   ```bash
   mvn clean verify sonar:sonar -Dsonar.token=<token> -Dsonar.host.url=http://localhost:9000 -Dsonar.scanner.skipJreProvisioning=true
   ```

5. Confirm the quality gate is `OK`.

### Common issues seen in this repo

| Rule | Severity | Typical fix |
|------|----------|-------------|
| `java:S3776` (cognitive complexity) | Critical | Extract helper methods; reduce branching |
| `java:S6068` (useless `eq()`) | Minor | Pass values directly instead of `eq(x)` |
| `java:S1659` (multiple declarations) | Minor | Put each declaration on its own line |
| `java:S1128` (unused import) | Minor | Remove the import |
| `java:S6126` (String concat → text block) | Major | Use a text block |
| `java:S5853` (split assertion) | Minor | Join assertions into one chain |
| `java:S5778` (exception in lambda) | Major | Extract the throwing call out of the lambda |
| `java:S5841` (empty-list assertion) | Minor | Precede with `isNotEmpty()` or use `noneSatisfy` |

## Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| Analysis hits `api.sonarcloud.io` / HTTP 403 | JRE provisioning unsupported on Community Edition; pass `-Dsonar.scanner.skipJreProvisioning=true` |
| Scan uses stale results | Old `target/` classes; run `mvn clean verify sonar:sonar` |
| Container fails to start | `vm.max_map_count` not needed here (Docker Desktop); check `docker compose ... ps` and logs |
| Quality gate still red after fixing | Coverage gate is on new code; add tests for the refactored/uncovered branches |

## Related

- [Getting Started](../guides/getting-started.md)
- [Observability](../guides/observability.md)
- [Architecture Overview](../architecture/overview.md)
- [ADR: SonarQube Static Analysis](../architecture/decision-records/adr-0004-sonarqube-static-analysis.md)
