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
6. **File length**: Files under 200 lines (ArchUnit rule).

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

This test class uses `com.tngtech.archunit.junit.AnalyzeClasses` and `ArchTest` rules to verify:
- Package dependency rules
- Class dependency rules
- Naming conventions
- File length limits

## Definition of Done

Refuse to mark a task complete until:
1. `.\scripts\test.bat --architecture` passes
2. `.\scripts\test.bat --coverage` shows ≥ 85% coverage on changed modules
3. No files exceed 200 lines
4. No circular dependencies introduced
