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
- **Assertion Helpers**: Use shared assertion helpers from `TestAssertions` in
  `rag-common/testfixture/` (see rules skill, section 7). Do NOT duplicate status +
  body assertion sequences across test files. Always import via
  `import static com.rag.common.testfixture.TestIngestionAssertions.*` or
  `import static com.rag.common.testfixture.TestControllerAssertions.*`.

## 4a. Shared Test Fixtures

Any test data value (object, string, byte array) used in 2+ test files MUST be
in a shared fixture class rather than constructed inline in each test.

## 4b. Parameterized Tests

Convert to `@ParameterizedTest` when a test class has 2+ `@Test` methods with
the **same test structure** but **different inputs/assertions**. Use:
- `@CsvSource` for simple inline data (strings, numbers, booleans).
- `@MethodSource` for complex objects (`Chunk`, `Document`, etc.) or when data
  is shared across test classes.
- Each parameterized case MUST have a descriptive `name` attribute:
  `@ParameterizedTest(name = "should reject empty title when title is '{0}'")`.

**Do NOT parameterize** when: each case has genuinely different setup/teardown,
when there is only one case, or when parameterization would hurt readability.

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
