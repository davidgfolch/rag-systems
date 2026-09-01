---
name: rag-architecture-tester
description: Run and enforce architecture tests (ArchUnit) and code quality rules across all RAG modules.
---

# RAG Architecture Tester

Use this skill when verifying architecture compliance, or before/after significant changes.

## Purpose
Enforce the architecture rules defined in `.claude/rules/architecture-guidelines.md` using ArchUnit.

## Rules Enforced

1. **Layer dependencies**: domain → services → repositories → config (no skipping, no circular deps).
2. **Interface usage**: Controllers/Services depend on interfaces, not concrete stores/models.
3. **Repository pattern**: No direct DB access from services.
4. **Naming conventions**: Packages `com.rag.[module].[layer]`, test class/method naming.
5. **No hardcoded providers**: Type-safe access to configured models (local/cloud), never hardcoded.
6. **File length**: Files under 200 lines (guideline; warn at 250).
7. **Minimal root**: Root holds only the files needed for the GitHub landing page and the build (enforced by `rag-common` `ArchitectureTest.repoRootContainsOnlyAllowedEntries`).
8. **Docs/scripts placement**: All `.md` in `docs/`, all `.bat`/`.sh`/`.ps1` in `scripts/`; only `README.md` allowed at root (enforced by `.noDocsOrScriptsAtRoot`).
9. **Portability**: Every operational script has a `.sh` + `.bat`/`.ps1` twin (enforced by `.scriptsArePortable`).

## How to Run

```bash
# Run all architecture tests across modules
.\scripts\test.bat --architecture   # Windows
./scripts/test.sh --architecture    # Linux/Mac

# Run architecture tests for a specific module
.\scripts\test.bat rag-basic --architecture
```

## Architecture Test Structure

Each runnable module has a test class:

```
apps/[module]/src/test/java/com/rag/[module]/architecture/ArchitectureTest.java
```

The class-based modules use `ClassFileImporter` (layer/interface rules). The `rag-common` `ArchitectureTest` additionally contains filesystem checks (`java.nio.file.Files`) that verify repository hygiene from the repo root:
- `repoRootContainsOnlyAllowedEntries` - minimal root (README + badges at a glance)
- `noDocsOrScriptsAtRoot` - docs live in `docs/`, scripts in `scripts/`
- `scriptsArePortable` - every script has a Windows (`.bat`/`.ps1`) + Unix (`.sh`) pair

## Definition of Done

Refuse to mark a task complete until:
1. `.\scripts\test.bat --architecture` passes (incl. the root-folder and portability checks)
2. `.\scripts\test.bat --coverage` shows ≥ 85% coverage on changed modules
3. No files exceed 200 lines
4. No circular dependencies introduced
5. Root folder stays minimal; no new `.md`/scripts outside `docs/`/`scripts/`
