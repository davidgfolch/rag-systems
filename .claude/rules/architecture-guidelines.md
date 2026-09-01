---
trigger: always_on
---

# Architecture Guidelines

Rules for all code in this repository. These enforce quality, maintainability, and consistency across all RAG modules.

## Code Quality Rules

1. **File Length**: Maximum 200 lines per file (warn at 250, fail at 300). If exceeded, refactor via SRP, abstraction, or composition.
2. **Method Length**: Maximum 30 lines per method.
3. **Parameters**: Maximum 5 parameters per method.
4. **Nesting**: Maximum 3 levels of nesting.
5. **Preserve Existing Code**: When editing files, only make changes necessary for the task. Do NOT reformat, reword, or restyle code that isn't being intentionally modified.
6. **Compact Style**: Use compact style for new code:
   - Keep parameters on same line when possible
   - Keep closing braces/parens on same line as last content
   - No extra spaces inside parentheses
   - Avoid empty lines inside method bodies

## Architecture Rules

1. **Layered Architecture per module**: domain → services → repositories → config. No skipping layers.
2. **No circular dependencies** between layers or modules.
3. **Interfaces define contracts** between layers (Strategy pattern for chunking/embedding/retrieval).
4. **Repository pattern** for all data access.
5. **Dependency injection** (Spring) for all components.
6. **Provider abstraction**: Never hardcode a specific model/provider. Use configuration profiles (local/cloud).
7. **DRY**: Common logic goes in rag-common. No duplicated code across modules.
8. **Composition over Inheritance**: Favor interfaces + delegation over class hierarchies.
9. **Reactive/Parallel**: Use Project Reactor and virtual threads strategically for I/O-bound operations; don't over-engineer simple flows.
10. **Single Responsibility**: Classes and methods do one thing. Split "fat" classes.

## Naming Conventions

- **Packages**: `com.rag.[module].[layer]`
- **Classes**: PascalCase (e.g., `DocumentIngestionService`)
- **Methods**: camelCase (e.g., `ingestDocument()`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_CHUNK_SIZE`)
- **Test Classes**: `[ClassName]Test` (e.g., `DocumentIngestionServiceTest`)
- **Test Methods**: `should[Behavior]When[Condition]` (e.g., `shouldReturnChunksWhenQueryIsRelevant`)

## Test Rules

1. **Test location**: Tests in `src/test/java/[module]/[layer]/` parallel to source.
2. **SUT instance**: Variable name for the service/class under test is `sut`.
3. **Parameterized tests**: Use JUnit `@ParameterizedTest` with `@CsvSource`/`@MethodSource` with descriptive names.
4. **Mocking**: Mock all external dependencies (LLMs, vector stores, DB) for unit tests.
5. **Performance**: Unit tests must run quickly (< 500ms).
6. **Coverage**: Minimum 85% per module (enforced by JaCoCo).
7. **Architecture test**: Every module has an `ArchitectureTest` enforcing the layer/dependency rules.

## Repository Organization Rules

1. **Minimal root**: Project root holds only the files needed for the GitHub landing page and the build. Keep the root file count to the minimum possible so `README.md` and its badges show at a glance.
2. **Docs placement**: All documentation (`.md`) lives in `docs/`. The only `.md` allowed at the project root is `README.md`.
3. **Scripts placement**: All executable scripts (`.bat`, `.sh`, `.ps1`) live in `scripts/`. None at the root. Maven wrapper (`mvnw`, `mvnw.cmd`) is the exception.
4. **Portability**: The project must be portable across Windows and Linux/Mac. Every operational script needs both a Windows variant (`.bat` or `.ps1`) and a Unix variant (`.sh`) with identical behavior. Prefer cross-platform tools (`curl`, `jq`/PowerShell) over shell-only assumptions.
5. **Enforcement**: Rules 1-4 are enforced by the filesystem checks in `rag-common` `ArchitectureTest` (`repoRootContainsOnlyAllowedEntries`, `noDocsOrScriptsAtRoot`, `scriptsArePortable`).

## Verification

Always run architecture tests after significant changes:

```bash
.\scripts\test.bat --architecture   # Windows
./scripts/test.sh --architecture    # Linux/Mac
```

## Best Practices

- Use the simplest SOLID implementation possible
- High cohesion and low coupling between components
- Write unit tests for all layers
- Test architecture via ArchUnit
- Keep classes and methods focused and small
- Document architecture decisions and patterns in docs/
- Ensure scalability and maintainability
