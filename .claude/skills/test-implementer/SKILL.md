---
name: test-implementer
description: Implement and run tests adhering to valid architecture and best practices (TDD, unit, integration).
---

# Test Implementer Instructions

Use this skill when implementing or running tests for the RAG Systems monorepo.

## 1. TDD Workflow
- Write tests BEFORE implementation (Red-Green-Refactor).
- Start with a failing test, then implement the minimal code to pass it, then refactor.

## 2. Test Location & Structure
- **Location**: Tests MUST be in `src/test/java/[module]/[layer]/` parallel to the production source folder.
  - Example: production `apps/rag-basic/src/main/java/com/rag/basic/ingestion/DocumentIngestionService.java`
  - Test: `apps/rag-basic/src/test/java/com/rag/basic/ingestion/DocumentIngestionServiceTest.java`
- **Separation**: Test code strictly separated from production code.
- **Fixtures/Mocks**: Extract common setup into separate helper classes (`TestFixtures.java`, `TestMocks.java`) when they grow large.

## 3. Naming Conventions
- **SUT instance**: Variable name for the service/class under test MUST be `sut`.
- **Test Class**: `[ClassName]Test`.
- **Test Method**: `should[Behavior]When[Condition]`.

## 4. Coding Best Practices
- **Abstraction**: Avoid duplicated code. Extract common setup into fixtures/mocks helpers.
- **Constants**: Reuse production code constants; do NOT duplicate string literals or magic numbers in tests.
- **Parameterized Tests**: Use JUnit `@ParameterizedTest` with `@CsvSource`/`@MethodSource`. Each case must have a descriptive name.
- **Performance**: Unit tests MUST run quickly (< 500ms each).
- **Mocking**: Mock all external layers (LLMs, vector stores, databases, file system, network) with Mockito.
- **Provider abstraction**: Test with a mocked/spied `EmbeddingModel` and `VectorStore`; never call real providers in unit tests.

## 5. Test Types
- **Unit tests**: Focused on single service/class with mocked dependencies.
- **Integration tests**: Use TestContainers (PgVector, PostgreSQL address). Tag with `@Tag("integration")`.
- **Architecture tests**: ArchUnit enforcing layer dependency rules. Tag with `@Tag("architecture")`.

## 6. Architecture Verification
Refuse to complete the task without verifying architecture compliance (architecture tests always run as part of the suite):

```bash
.\scripts\test.bat   # Windows
./scripts/test.sh    # Linux/Mac
```

## Usage
1. Create `src/test/java/[module]/[layer]/[Class]Test.java`.
2. Instantiate `sut = new [Class](mockedDeps)`.
3. Run tests: `.\scripts\test.bat [module]`.
4. Run architecture tests to enforce rules.
