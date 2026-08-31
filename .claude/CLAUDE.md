# CLAUDE.md

This file provides guidance to Claude and other agentic coding assistants working with code in this repository.

## Project Overview

RAG Systems is a monorepo for learning and comparing different RAG (Retrieval-Augmented Generation) implementations using Java Spring Boot and Spring AI. The project ingests documents (PDF, DOCX, HTML, web pages) and provides different retrieval architectures for comparison, with full observability and benchmarking.

## Monorepo Structure

| Module | Path | Description | Package Manager |
|--------|------|-------------|-----------------|
| **rag-common** | `apps/rag-common/` | Shared domain, services, repositories | Maven |
| **rag-basic** | `apps/rag-basic/` | Basic RAG with fixed chunking | Maven |
| **rag-advanced** | `apps/rag-advanced/` | Advanced RAG, reranking, hybrid search | Maven |
| **rag-agentic** | `apps/rag-agentic/` | Agentic RAG with tool calling | Maven |
| **rag-evaluation** | `apps/rag-evaluation/` | Metrics, benchmarking, comparison | Maven |
| **rag-observability** | `apps/rag-observability/` | Tracing, metrics, dashboards | Maven |
| **rag-cli** | `apps/rag-cli/` | Interactive CLI for querying | Maven |

## Build & Test Commands

All operations are centralized in the `scripts/` folder. Use the scripts, not direct Maven commands, unless debugging.

```bash
# Install all dependencies
.\scripts\install.bat          # Windows
./scripts/install.sh           # Linux/Mac

# Run all tests
.\scripts\test.bat             # Windows
./scripts/test.sh              # Linux/Mac

# Run tests with coverage
.\scripts\test.bat --coverage

# Run specific module tests
.\scripts\test.bat rag-basic

# Run architecture tests
.\scripts\test.bat --architecture

# Run a module
.\scripts\run.bat rag-basic --profile local

# Docker operations
.\scripts\docker.bat up
.\scripts\docker.bat up-obs
```

## Architecture Patterns

### Layered Architecture per Module
```
domain/       - Domain models and business rules (DDD)
services/     - Business logic, use cases
repositories/ - Data access layer (repository pattern)
config/       - Spring configuration
api/          - REST controllers (only in runnable modules)
```

### Interfaces / Strategy Pattern
- Chunking strategies are interchangeable via the `TextSplitter` interface
- Embedding models are interchangeable via the `EmbeddingModel` interface
- Retrieval strategies are interchangeable via the `Retriever` interface
- Vector stores are interchangeable via the `VectorStore` interface

### Dependency Rules
- `domain` depends on nothing
- `services` depends on `domain` + interfaces
- `repositories` depends on `domain` + Spring Data
- `config` wires everything
- No circular dependencies between layers
- No direct database access from Services

## Key Conventions

1. **File Length**: Maximum 200 lines (warn at 250, fail at 300)
2. **Test Location**: Tests in `src/test/java/[module]/[layer]/[Class]Test.java`
3. **Test Naming**: `[ClassName]Test` for classes, `should[Behavior]When[Condition]` for methods
4. **DRY**: Extract common logic to rag-common; no duplicated code
5. **Composition over Inheritance**: Favor interfaces + delegation over class hierarchies
6. **Reactive/Parallel**: Use Project Reactor and virtual threads strategically for I/O-bound operations
7. **Provider Abstraction**: Never hardcode provider-specific code; use configuration profiles
8. **Documentation**: Avoid explanatory comments; code should be self-documenting

## Configuration Profiles

- **local**: Ollama models (nomic-embed-text, phi4/qwen3) - default for development
- **cloud**: OpenAI/Anthropic models - for frontier-model comparison
- **observability**: Enables OpenTelemetry, Micrometer, Prometheus, Grafana

## Architecture Tests

Always run after significant changes:

```bash
.\scripts\test.bat --architecture
```

This runs ArchUnit tests enforcing layer dependencies, file length limits, and naming conventions.

## Definition of Done

Before marking any task complete, verify:
1. **No architecture violations**: Run `scripts\test.bat --architecture`
2. **All tests pass**: Run `scripts\test.bat [module]`
3. **Coverage ≥ 85%** for changed modules
4. **No duplicate code**: Common logic in rag-common
5. **Provider abstraction**: No hardcoded model/provider references

## Skills

Agent skills are in `.claude/skills/`:
- `test-implementer`: Implement unit/integration tests (JUnit 5, Mockito)
- `rag-architecture-tester`: Run and enforce architecture tests (ArchUnit)
- `documentation-generator`: Generate module documentation and diagrams
- `sonarqube-analyzer`: Run and fix SonarQube static analysis findings

## Observability

Each query and ingestion job produces:
- **Spans**: OpenTelemetry trace through the full RAG pipeline
- **Metrics**: Micrometer (ingestion, retrieval, generation, quality, cost)
- **Logs**: Structured JSON with traceId/spanId correlation

See `docs/guides/observability.md` for dashboard setup.
