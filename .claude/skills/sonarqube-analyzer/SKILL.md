---
name: sonarqube-analyzer
description: Run and interpret SonarQube static analysis (bugs, code smells, coverage, quality gate) on the RAG Systems monorepo, and fix its findings.
---

# SonarQube Analyzer

Use this skill when running SonarQube static analysis, interpreting its findings, fixing issues, or enforcing the "Clean as You Code" quality gate.

## Purpose
Run local SonarQube static analysis on the monorepo, map findings to fixes, and keep the quality gate green.

## Key Facts

- **Server**: local SonarQube Community 10.7 LTS at `http://localhost:9000` (`admin`/`admin` first login).
- **Project key**: `com.rag:rag-systems` (derived from the parent POM GAV).
- **Entry points**: `scripts/sonar.{bat,sh}` and the `sonar-maven-plugin`. Results are auto-exported to the README by `scripts/sonar-export.ps1` (Windows) / `scripts/sonar-export.sh` (Linux/Mac) after each scan.
- **Config**: `sonar-project.properties` (exclusions, JRE-provisioning skip, report paths), `docker/docker-compose.sonarqube.yml`.
- **Docs**: `docs/guides/sonarqube.md`, `docs/architecture/decision-records/adr-0004-sonarqube-static-analysis.md`.

## Workflow

### 1. Generate / reuse a token
Token is passed as the 2nd arg or via `SONAR_TOKEN` env var. Generate one in the SonarQube UI (**My Account → Security**) only the very first time, or generate one programmatically via the REST API against the local instance (default credentials `admin`/`admin` on the local Community server):

```bash
# Create a token via the API (returns the token in JSON)
curl -s -u admin:admin -X POST "http://localhost:9000/api/user_tokens/generate" -d "name=rag-local-ci"

# Set it for the scan
export SONAR_TOKEN=squ_xxxx   # Linux/Mac
set SONAR_TOKEN=squ_xxxx       # Windows (cmd)
$env:SONAR_TOKEN="squ_xxxx"    # Windows (PowerShell)
```

The token starts with `squ_` and is shown only once at creation; store it in your shell/env for reuse. Tokens can be revoked later under **My Account → Security**.

### 2. Start the server + run analysis
Analysis always runs the full test suite first (`mvn verify` with JaCoCo), then the scanner:

```bash
# Windows (PowerShell/cmd)
.\scripts\sonar.bat up-scan $env:SONAR_TOKEN
.\scripts\sonar.bat scan $env:SONAR_TOKEN

# Linux/Mac
./scripts/sonar.sh up-scan $SONAR_TOKEN
./scripts/sonar.sh scan $SONAR_TOKEN
```

### 3. Read the results
- Dashboard: `http://localhost:9000` → project `com.rag:rag-systems`.
- **Quality gate conditions** (`api/qualitygates/project_status`): `new_coverage ≥ 80%`, `new_duplicated_lines_density < 3%`, `new_violations == 0`.
- The Maven build does **not** fail on a red gate by default; always check the dashboard/API for gate status.

## Fixing Findings

1. Open the flagged rule; read the explanation.
2. Make the minimal change (extract a helper, remove dead code, split declarations, add a test for an uncovered branch, etc.).
3. Re-scan with `clean` so stale `target/` classes never mask a fix:

```bash
mvn clean verify sonar:sonar -Dsonar.token=<token> -Dsonar.host.url=http://localhost:9000 -Dsonar.scanner.skipJreProvisioning=true
```

4. Confirm the gate is `OK` via the dashboard or API.

## Quality Gate on Refactors

The gate measures **new/modified lines**. Refactoring a method (e.g., splitting a large method into helpers) counts the refactored lines as "new code" and can drop `new_coverage` until those branches are covered by tests. Always add tests for any newly introduced branches.

## Common Rules Seen in This Repo

| Rule | Meaning / fix |
|------|----------------|
| `java:S3776` | Cognitive complexity too high → extract helper methods, reduce branching |
| `java:S6068` | Useless `eq(...)` in Mockito → pass the value directly |
| `java:S1659` | Multiple declarations on one line → one declaration per line |
| `java:S1128` | Unused import → remove it |
| `java:S6126` | String concatenation → use a text block |
| `java:S5853` | Split assertions → join into one chain |
| `java:S5778` | Multiple throwing calls in a lambda → extract out of the lambda |
| `java:S5841` | Empty-list handling in an assertion → precede with `isNotEmpty()` or use `noneSatisfy` |

## Definition of Done

Refuse to mark the task complete until:
1. `.\scripts\sonar.bat scan <token>` (or `sonar.sh`) runs the full suite without errors.
2. The SonarQube **quality gate is `OK`**.
3. `new_coverage` on changed code is `≥ 80%`.
4. Architecture tests still pass: `.\scripts\test.bat --architecture` (or run `ArchitectureTest` via Maven).

## Gotchas

- **JRE provisioning**: Community Edition must scan with `-Dsonar.scanner.skipJreProvisioning=true` (otherwise it hits SonarCloud endpoints / HTTP 403).
- **`-D` flags required**: The Maven plugin does not reliably read `sonar.host.url`/`sonar.projectKey`/JaCoCo paths from `sonar-project.properties`; pass them via CLI.
- **Stale results**: always use `mvn clean verify sonar:sonar` after source changes.
- **PowerShell `.` splitting**: quote `-D` properties when invoking `mvnw.cmd` from PowerShell (`"-Dsonar.host.url=http://localhost:9000"`).
