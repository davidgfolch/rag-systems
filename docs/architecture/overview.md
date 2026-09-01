# RAG Systems Architecture Overview

This document describes the high-level architecture of the RAG Systems monorepo.

## Architecture at a Glance

```
┌─────────────────────────────────────────────────────────────────────┐
│                        RAG Systems Monorepo                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                      Layers per Module                      │   │
│  │  ┌────────────┐  ┌────────────┐  ┌───────────────────────┐ │   │
│  │  │   domain   │→ │  services  │→ │     repositories      │ │   │
│  │  │ (entities) │  │ (use cases)│  │ (data access, VectorStore)│ │   │
│  │  └────────────┘  └────────────┘  └───────────────────────┘ │   │
│  │                            ↓                                │   │
│  │                    ┌──────────────┐                         │   │
│  │                    │    config    │  (wires everything)     │   │
│  │                    └──────────────┘                         │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌───────────────┬───────────────┬───────────────┬───────────────┐ │
│  │  rag-common   │   rag-basic   │ rag-advanced  │ rag-agentic   │ │
│  │ (shared lib)  │ (basic RAG)   │ (prod RAG)    │ (agents)      │ │
│  ├───────────────┼───────────────┼───────────────┼───────────────┤ │
│  │ Domain models │ Fixed chunk   │ Semantic chunk│ Tool calling  │ │
│  │ Interfaces    │ Similarity    │ Reranking     │ Multi-step    │ │
│  │ VectorStore   │ search        │ Hybrid search │ Self-reflect  │ │
│  └───────────────┴───────────────┴───────────────┴───────────────┘ │
│                                                                     │
│  ┌───────────────┬───────────────┬───────────────┬───────────────┐ │
│  │ rag-evaluation│ rag-observ-   │    rag-cli    │               │ │
│  │ (metrics)     │  ability      │  (TUI testing)│               │ │
│  ├───────────────┼───────────────┼───────────────┼───────────────┤ │
│  │ Precision     │ Tracing       │ Interactive   │               │ │
│  │ Recall, MRR   │ Metrics       │ queries       │               │ │
│  │ Benchmark     │ Dashboards    │               │               │ │
│  └───────────────┴───────────────┴───────────────┴───────────────┘ │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

## Layered Architecture

Each runnable module follows a strict layered architecture with **one-way dependency flow**:

1. **domain/** - Entities (Document, Chunk, Embedding), value objects, domain logic. Depends on nothing.
2. **services/** - Business logic / use cases. Depends on domain + interfaces.
3. **repositories/** - Data access, implements interfaces. Depends on domain + Spring Data.
4. **config/** - Spring configuration, wires concrete implementations.

**Rules:**
- No skipping layers (services cannot directly use repositories)
- No circular dependencies
- Interfaces define contracts between layers (Open-Closed Principle)
- Composition over inheritance - pipelines compose strategy implementations

## Cross-Cutting Concerns

### Provider Abstraction (Critical)
The `EmbeddingModel` and chat model abstractions must allow swapping between OpenAI, Ollama, and HuggingFace via **configuration profiles**, not code changes.

| Profile | Embeddings | LLM | Use case |
|---------|-----------|-----|----------|
| `local` | nomic-embed-text (Ollama) | phi4 / qwen3 (Ollama) | Development, privacy |
| `cloud` | text-embedding-3-small | gpt-4o | Frontier-model comparison |

### Strategy Pattern
Pluggable strategies via interfaces:
- `TextSplitter` - chunking strategies
- `EmbeddingModel` - embedding providers
- `Retriever` - retrieval strategies
- `VectorStore` - storage backends

### Repository Pattern
The `VectorStore` interface abstracts storage so PgVector, in-memory stores can be swapped transparently.

### Reactive/Parallel
- Java 21 virtual threads for blocking I/O (LLM/embedding calls)
- Project Reactor (Flux/Mono) for composable async
- Parallel processing for batch ingestion/embedding

## RAG Pipeline

### Ingestion Pipeline
```
Document → Parser → TextSplitter → EmbeddingModel → VectorStore
```

### Retrieval Pipeline
```
Query → QueryEmbedding → Retriever → ContextAssembly → LLM → Answer
```

### Observability (cross-cutting)
```
OpenTelemetry spans + Micrometer metrics + Structured logs
```

## Module Roles

| Module | Responsibility |
|--------|---------------|
| rag-common | Shared domain models, interfaces, configuration helpers |
| rag-basic | Baseline RAG: fixed/recursive chunking, similarity search |
| rag-advanced | Reranking, hybrid search, semantic chunking, metadata filtering |
| rag-agentic | Agents, tool calling, multi-step retrieval, self-reflection |
| rag-evaluation | Metrics (precision, recall, MRR), benchmarking, comparison |
| rag-observability | Tracing, metrics, structured logging, dashboards |
| rag-cli | Interactive command-line testing of RAG queries |

## Local Machine Feasibility

The design supports running fully on a regular local machine:
- **Embeddings**: small models run fast on CPU (~580 chunks/sec with nomic)
- **Vector store**: PgVector runs locally, or use in-memory store
- **LLM generation**: use small models (3B-4B) on CPU or a modest GPU (8GB) for comfort

See [performance-metrics.md](../comparison/performance-metrics.md) for details.

## Related Documents

- [Master Plan](../PLAN.md)
- [Getting Started](../guides/getting-started.md)
- [Chunking Strategies](../guides/chunking-strategies.md)
- [Observability](../guides/observability.md)
- [Performance Metrics](../comparison/performance-metrics.md)
- [Trade-offs](../comparison/trade-offs.md)
