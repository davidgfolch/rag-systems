# RAG Systems - Master Plan

Master architecture plan for learning and comparing different RAG (Retrieval-Augmented Generation) implementations using the Java Spring ecosystem. The project supports document ingestion (PDF, docs, web-sites) about any kind of knowledge, designed to be reusable across domains.

## Project Overview

- **Goal**: Learn and compare different RAG architectures
- **Focus**: Document ingestion, chunking, embedding, and search approaches
- **Reusability**: Decoupled modules usable for any knowledge domain
- **Output**: Quantitative performance comparison of approaches

## Architecture Principles

### Core Principles

**DRY (Don't Repeat Yourself)**
- Every piece of knowledge must have a single, unambiguous, authoritative representation
- Extract common logic into shared services and utilities
- Reuse production code constants in tests - no magic numbers or string literals

**KISS (Keep It Simple, Stupid)**
- Simpler solutions are easier to understand and maintain
- Avoid premature optimization
- Use the simplest SOLID implementation possible

**YAGNI (You Aren't Gonna Need It)**
- Don't build functionality until it's actually required
- Focus on current requirements, not hypothetical future needs
- Refactor when requirements change, not before

**SoC (Separation of Concerns)**
- Separate business logic, APIs, and repositories
- Each module should have a single, well-defined responsibility
- Different aspects of the system should be isolated

**Composition over Inheritance**
- Favor object composition over class inheritance
- Build complex behavior by composing small, focused components
- Use interfaces and delegation instead of deep inheritance hierarchies
- Example: `RagPipeline` composes `DocumentParser`, `TextSplitter`, `EmbeddingModel`, `VectorStore` instead of inheriting from a base pipeline class

**Reactive/Parallel Processing (When Applies)**
- Use Project Reactor (Mono/Flux) for I/O-bound operations
- Parallelize independent tasks: document ingestion, batch embedding, concurrent queries
- Leverage Java 21 virtual threads for blocking I/O
- Apply parallel streams for CPU-bound chunking operations
- Avoid reactive for simple synchronous flows - only when performance benefit is clear

**SRP (Single Responsibility Principle)**
- Each class should have only one reason to change
- Each method should do one thing well
- Keep classes and methods focused and small (< 200 lines)

**OCP (Open-Closed Principle)**
- Software entities should be open for extension, closed for modification
- Use interfaces and abstract classes for extensibility
- New chunking strategies should not require changing existing code

**LSP (Liskov Substitution Principle)**
- Subtypes must be substitutable for their base types
- All implementations of interfaces must honor their contracts
- Tests should work with any implementation

**ISP (Interface Segregation Principle)**
- Clients should not depend on interfaces they don't use
- Create small, focused interfaces over large, general-purpose ones
- Split "fat" interfaces into smaller, role-specific ones

**DIP (Dependency Inversion Principle)**
- Depend on abstractions, not concretions
- High-level modules should not depend on low-level modules
- Use Spring's dependency injection for all components

**CoC (Convention over Configuration)**
- Use sensible defaults to reduce configuration
- Follow Spring Boot's naming conventions
- Leverage auto-configuration where possible

**LoD (Law of Demeter)**
- Don't talk to strangers - only talk to your immediate collaborators
- Avoid method chaining that reaches into other objects
- Keep coupling loose between components

### Architecture Patterns

**TDD (Test-Driven Development)**
- Write tests before implementation
- Red-Green-Refactor cycle
- Architecture tests enforce code quality

**DDD (Domain-Driven Design)**
- Bounded contexts for each RAG module
- Ubiquitous language in domain models
- Rich domain models with behavior

**Clean Architecture**
- Dependencies point inward toward the domain
- Infrastructure details are on the outer layer
- Business logic is independent of frameworks

**Repository Pattern**
- Abstract data access behind interfaces
- VectorStore interface with multiple implementations
- Easy to swap PgVector for other stores

**Strategy Pattern**
- Different chunking strategies as interchangeable components
- Embedding models as strategy implementations
- Retrieval strategies can be swapped at runtime

### Naming Conventions

- **Packages**: `com.rag.[module].[layer]` (e.g., `com.rag.common.domain`)
- **Classes**: PascalCase (e.g., `DocumentIngestionService`)
- **Methods**: camelCase (e.g., `ingestDocument()`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_CHUNK_SIZE`)
- **Test Classes**: `[ClassName]Test` (e.g., `DocumentIngestionServiceTest`)
- **Test Methods**: `should[Behavior]When[Condition]` (e.g., `shouldReturnChunksWhenQueryIsRelevant`)

### Code Quality Rules

1. **File Length**: Maximum 200 lines (warn at 250, fail at 300)
2. **Method Length**: Maximum 30 lines per method
3. **Parameters**: Maximum 5 parameters per method
4. **Nesting**: Maximum 3 levels of nesting
5. **Duplication**: No duplicated code - extract to shared utilities
6. **Comments**: Code should be self-documenting; avoid unnecessary comments
7. **Imports**: No wildcard imports; explicit imports only
8. **Null Safety**: Use Optional instead of null returns

## Technology Stack (LTS Versions)

| Component | Version | Notes |
|-----------|---------|-------|
| Java | 21 LTS | Virtual threads, records, sealed classes |
| Spring Boot | 3.4.x | Latest stable |
| Spring AI | 1.1.x | Latest stable |
| Maven | 3.9.x | Multi-module parent POM |
| PostgreSQL | 16 LTS | For PgVector |
| Node | 20 LTS | For CLI tooling |

### Dependencies
- **Document Processing**: Apache Tika, JSoup, Apache PDFBox
- **Vector Store**: PgVector (PostgreSQL extension)
- **Embeddings**: OpenAI text-embedding-3-small, Ollama (local)
- **LLMs**: OpenAI GPT-4o, Anthropic Claude, Ollama (local)
- **Observability**: Micrometer, OpenTelemetry, Prometheus, Grafana
- **Testing**: JUnit 5, Mockito, TestContainers, ArchUnit

## Project Structure

```
rag-systems/
├── .claude/                          # Agentic SDLC configuration
│   ├── CLAUDE.md                     # Project guidance for agentic assistants
│   ├── rules/
│   │   └── architecture-guidelines.md
│   └── skills/
│       ├── test-implementer/
│       ├── rag-architecture-tester/
│       └── documentation-generator/
├── .opencode/                        # Opencode configuration
│   └── opencode.json
├── apps/                             # Monorepo modules
│   ├── rag-common/                   # Shared library
│   │   ├── src/main/java/com/rag/common/
│   │   │   ├── domain/              # Document, Chunk, Embedding
│   │   │   ├── services/            # Parser, Splitter interfaces
│   │   │   ├── repositories/        # VectorStore interface
│   │   │   └── config/              # Configuration
│   │   └── src/test/java/com/rag/common/
│   ├── rag-basic/                    # Basic RAG implementation
│   │   ├── src/main/java/com/rag/basic/
│   │   │   ├── ingestion/           # Document ingestion
│   │   │   ├── chunking/            # Fixed, recursive, token-based
│   │   │   ├── embedding/           # Embedding generation
│   │   │   ├── vectorstore/         # PgVector, in-memory
│   │   │   └── retrieval/           # Similarity search
│   │   └── src/test/java/com/rag/basic/
│   ├── rag-advanced/                 # Advanced RAG
│   │   ├── src/main/java/com/rag/advanced/
│   │   │   ├── reranking/           # Reranking strategies
│   │   │   ├── hybrid/              # Hybrid search
│   │   │   └── metadata/            # Metadata filtering
│   │   └── src/test/java/com/rag/advanced/
│   ├── rag-agentic/                  # Agentic RAG
│   │   ├── src/main/java/com/rag/agentic/
│   │   │   ├── agents/              # AI agents
│   │   │   ├── tools/               # Tool implementations
│   │   │   └── orchestration/       # Agent orchestration
│   │   └── src/test/java/com/rag/agentic/
│   ├── rag-evaluation/              # Metrics and benchmarking
│   │   ├── src/main/java/com/rag/evaluation/
│   │   │   ├── metrics/             # Evaluation metrics
│   │   │   ├── benchmark/           # Benchmarking
│   │   │   └── comparison/          # Comparison tools
│   │   └── src/test/java/com/rag/evaluation/
│   ├── rag-observability/           # Observability stack
│   │   ├── src/main/java/com/rag/observability/
│   │   │   ├── config/              # OpenTelemetry, Metrics config
│   │   │   ├── metrics/             # Ingestion, Retrieval, Generation metrics
│   │   │   ├── tracing/             # Pipeline spans
│   │   │   └── logging/             # Structured logging
│   │   ├── src/main/resources/
│   │   │   ├── grafana/
│   │   │   └── prometheus/
│   │   └── src/test/java/com/rag/observability/
│   └── rag-cli/                      # CLI tool for testing
│       ├── src/main/java/com/rag/cli/
│       └── src/test/java/com/rag/cli/
├── scripts/                          # Centralized operations
│   ├── install.bat/.sh             # Install dependencies
│   ├── test.bat/.sh                # Run tests
│   ├── build.bat/.sh               # Build modules
│   ├── run.bat/.sh                 # Run modules
│   ├── docker.bat/.sh              # Docker operations
│   └── sonar.bat/.sh               # SonarQube static analysis
├── docs/                             # Documentation
│   ├── architecture/
│   │   ├── decision-records/
│   │   └── diagrams/
│   ├── guides/
│   │   ├── getting-started.md
│   │   ├── chunking-strategies.md
│   │   ├── vector-stores.md
│   │   ├── observability.md
│   │   └── sonarqube.md
│   └── comparison/
│       ├── performance-metrics.md
│       └── trade-offs.md
├── docker/                           # Docker configurations
│   ├── docker-compose.yml
│   ├── docker-compose.observability.yml
│   ├── docker-compose.sonarqube.yml
│   └── Dockerfile
├── sonar-project.properties          # SonarQube scanner settings
├── pom.xml                           # Parent POM
└── README.md
```

## Module Architecture

### 1. rag-common (Shared Library)

Core domain models, services, and utilities shared across all RAG modules.

- `domain/Document.java` - Core document entity with metadata
- `domain/Chunk.java` - Chunk entity with embeddings
- `domain/Embedding.java` - Embedding vector representation
- `services/DocumentParser.java` - Document parsing interface
- `services/TextSplitter.java` - Text splitting interface
- `services/EmbeddingModel.java` - Embedding generation interface
- `repositories/VectorStore.java` - Vector storage interface
- `config/SpringAiConfig.java` - Spring AI configuration

### 2. rag-basic (Basic RAG)

Simple RAG with fixed chunking and basic similarity search.

**Chunking**: Fixed-size sliding window, recursive character, token-based
**Vector Stores**: SimpleVectorStore (in-memory), PgVector
**Features**: PDF/DOCX/HTML ingestion, basic similarity search, prompt augmentation

### 3. rag-advanced (Advanced RAG)

Production-grade RAG with reranking, hybrid search, and metadata filtering.

**Features**: Reranking (Cohere/LLM), hybrid search (vector + keyword), metadata filtering, query transformation, contextual compression

### 4. rag-agentic (Agentic RAG)

RAG with AI agents and tool calling for dynamic retrieval.

**Features**: Query planning agents, multi-step retrieval, tool calling, self-reflective RAG

### 5. rag-evaluation (Evaluation)

Metrics and benchmarking for comparing RAG implementations.

**Metrics**: Retrieval precision/recall, answer relevance, faithfulness, MRR, latency, throughput

### 6. rag-observability (Observability)

Monitoring, tracing, and metrics for RAG systems.

**Stack**: Micrometer, OpenTelemetry, Prometheus, Grafana
**Metrics**: Ingestion, retrieval, generation, quality metrics with cost tracking

### 7. rag-cli (CLI Tool)

Interactive CLI for testing RAG queries.

**Features**: Picocli commands, interactive query mode, batch query processing

## Local Development Profile

The project is designed to run comfortably on a **regular local machine** (CPU-only or modest GPU) for learning purposes. RAG separates into layers with very different hardware demands - the heavy compute is composable and optional.

### Layer Hardware Requirements

| Layer | Model | Memory | Hardware | Performance (CPU) |
|-------|-------|--------|----------|-------------------|
| Embeddings | `nomic-embed-text` | ~300MB | CPU ok | ~580 chunks/sec |
| Embeddings | `all-MiniLM-L6-v2` | ~90MB | CPU ok | ~100+ chunks/sec |
| Vector Store | PgVector | - | CPU ok | In-memory fast |
| Vector Store | SimpleVectorStore | - | CPU ok | Fastest |
| LLM (learning) | Qwen3 4B / Phi-4 | ~4GB | CPU ok (slow) | Usable |
| LLM (comfortable) | Qwen3 8B (Q4) | ~4-8GB VRAM | GPU (8GB) | Fast |
| LLM (best local) | Qwen3-30B-A3B (MoE) | ~16GB VRAM | GPU | Excellent |

### Recommended Local Configuration

| Component | Recommendation | Why |
|-----------|---------------|-----|
| Embeddings | `nomic-embed-text` (Ollama) | ~300MB, fast on CPU, 8k context |
| Vector store | PgVector + in-memory fallback | Both in plan |
| LLM (CPU, 16GB RAM) | Qwen3 4B / Phi-4 / Llama 3.2 3B | Usable, decent answers |
| LLM (8GB GPU) | Qwen3 8B (Q4_K_M) | Best balance, 128k context |
| Multilingual | `bge-m3` | For non-English tech docs |

### Hardware Guidance

- **Minimum**: 16GB RAM, no GPU required for embeddings/vector store
- **Recommended**: 16GB RAM + 8GB VRAM GPU for comfortable LLM generation
- **CPU-only**: Works, but LLM generation is slow (seconds/token for 7B+). Use small models (3B-4B).
- **GPU**: Optional, accelerates bulk indexing and unlocks larger/more accurate models

### Provider Abstraction

Critical design decision: the `EmbeddingModel` and LLM abstractions must allow seamless swapping between OpenAI, Ollama, and HuggingFace via **configuration, not code**. Strategy pattern handles this.

- `application-local.yml` - Points to Ollama default models
- `application-cloud.yml` - Points to OpenAI/Anthropic
- Profile selection via `SPRING_PROFILES_ACTIVE=local` or `=cloud`

### Local Model Setup (Ollama)

```bash
# Embedding model
ollama pull nomic-embed-text

# Small CPU-friendly LLMs
ollama pull phi4
ollama pull qwen3:4b

# GPU-friendly LLM (8GB VRAM+)
ollama pull qwen3:8b
```

## RAG Pipeline Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    RAG Pipeline Architecture                    │
├─────────────────────────────────────────────────────────────────┤
│  INGESTION PIPELINE                                            │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐ │
│  │ Document │───▶│  Text    │───▶│Embedding │───▶│ Vector   │ │
│  │ Reader   │    │ Splitter │    │ Model    │    │ Store    │ │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘ │
│                                                                 │
│  RETRIEVAL PIPELINE                                            │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐ │
│  │  User    │───▶│ Query    │───▶│ Retrieval│───▶│   LLM    │ │
│  │  Query   │    │ Embed    │    │ Search   │    │ Generate │ │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘ │
│                                                                 │
│  OBSERVABILITY LAYER (cross-cutting)                           │
│  Tracing (OTel) · Metrics (Meter) · Logging (Structured)      │
└─────────────────────────────────────────────────────────────────┘
```

## Observability Architecture

### Metrics Categories

**Ingestion**: documents processed, chunks created, duration, errors
**Retrieval**: query latency, vector search latency, chunks returned, similarity scores
**Generation**: input/output tokens, LLM latency, cost tracking
**Quality**: relevance, faithfulness, hallucination detection

### Tracing
Every request gets a `traceId` following the full pipeline:
```
User Query → Query Embedding → Vector Search → Context Assembly → LLM Generation → Response
```

### Grafana Dashboards
1. RAG Pipeline Overview - request rate, latency, error rates
2. Retrieval Performance - similarity scores, chunk patterns, search latency
3. Cost & Usage - token usage by provider, cost over time

## Chunking Strategy Comparison

| Strategy | Best For | Pros | Cons |
|----------|----------|------|------|
| Fixed-size | Homogeneous text | Fast, simple | Poor semantic coherence |
| Recursive | Most cases | Good balance | May split mid-sentence |
| Semantic | High-value docs | Best precision | Expensive (8x cost) |
| Agentic | Complex documents | Adaptive | Complex implementation |

## Test Strategy

- **Unit Tests**: 85%+ coverage, mocked dependencies, parameterized tests
- **Integration Tests**: TestContainers for PgVector, ingestion pipelines
- **Architecture Tests**: ArchUnit for dependency rules, file length limits
- **E2E Tests**: Complete pipeline, ingestion to response, benchmarks

## Scripts Centralization

All operations centralized in `scripts/` folder with Windows (`.bat`) and Linux/Mac (`.sh`) variants.

| Script | Purpose |
|--------|---------|
| `install` | Install dependencies (mvn clean install) |
| `test` | Run tests (with `--coverage`, `--architecture`, module selection) |
| `build` | Build all or specific modules |
| `run` | Run a specific module (with optional observability profile) |
| `docker` | Docker operations (base, observability, SonarQube, all services) |
| `sonar` | SonarQube static analysis (`up`/`scan`/`up-scan`/`down`) |

### Static Analysis (SonarQube)

Local **SonarQube Community 10.7 LTS** (Docker) runs `mvn verify sonar:sonar` for bugs, vulnerabilities, code smells, and coverage enforcement.

- **Compose overlay**: `docker/docker-compose.sonarqube.yml` (port `9000`, persistent volumes).
- **Scanner config**: `sonar-project.properties`; plugin in `pom.xml` (`sonar-maven-plugin`).
- **Quality gate**: "Clean as You Code" - new-code coverage ≥ 80%, no new violations, no new duplication.
- **Workflow**: `.\scripts\sonar.bat up-scan %SONAR_TOKEN%`, review at `http://localhost:9000` (project `com.rag:rag-systems`).

See [docs/guides/sonarqube.md](docs/guides/sonarqube.md) and [ADR-0004](docs/architecture/decision-records/adr-0004-sonarqube-static-analysis.md).

## Implementation Phases

### Phase 1: Foundation (Week 1-2)
- [ ] Setup monorepo structure
- [ ] Implement rag-common domain models
- [ ] Create document parser interfaces
- [ ] Setup test infrastructure with ArchUnit
- [ ] Create all scripts

### Phase 2: Basic RAG (Week 3-4)
- [ ] Implement fixed-size chunking
- [ ] Add recursive chunking
- [ ] Integrate PgVector
- [ ] Create basic query endpoint with Swagger

### Phase 3: Advanced RAG (Week 5-6)
- [ ] Add semantic chunking
- [ ] Implement reranking
- [ ] Add metadata filtering
- [ ] Create hybrid search

### Phase 4: Agentic RAG (Week 7-8)
- [ ] Implement query planning
- [ ] Add tool calling
- [ ] Create multi-step retrieval
- [ ] Add self-reflection

### Phase 5: Observability (Week 9)
- [ ] Implement OpenTelemetry tracing
- [ ] Add Micrometer metrics
- [ ] Create Grafana dashboards
- [ ] Setup Prometheus

### Phase 6: Evaluation & CLI (Week 10)
- [ ] Implement metrics collection
- [ ] Create benchmarking suite
- [ ] Generate comparison reports
- [ ] Create CLI tool

## Success Criteria

1. Multiple RAG implementations working independently
2. Performance comparison showing trade-offs
3. Architecture tests enforcing code quality
4. Comprehensive documentation for learning
5. Observability stack monitoring all operations
6. Reusable components for other projects
