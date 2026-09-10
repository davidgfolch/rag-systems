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
│  │ rag-evaluation│ rag-observ-   │    rag-cli    │ rag-provider  │ │
│  │ (metrics)     │  ability      │  (TUI testing)│ (model hub)   │ │
│  ├───────────────┼───────────────┼───────────────┼───────────────┤ │
│  │ Precision     │ Tracing       │ Interactive   │ Chat/embed    │ │
│  │ Recall, MRR   │ Metrics       │ queries       │ switching     │ │
│  │ Benchmark     │ Dashboards    │               │ Provider API  │ │
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
All provider connectivity is centralized in the **rag-provider** service: it owns the Ollama/OpenAI clients and exposes chat (`/api/complete`, `/api/chat/stream`), embedding (`/api/embed`), and model-switching (`/api/provider/*`) over HTTP. Downstream RAG modules used to hold direct provider dependencies; they now consume rag-provider through thin `rag-common` bridges (`RemoteChatModelPort`, `RemoteEmbeddingModel`). Swapping between OpenAI, Ollama, and any OpenAI-compatible provider is a runtime call — no code or config-profile change needed.

| Profile | Embeddings | LLM | Use case |
|---------|-----------|-----|----------|
| `local` | nomic-embed-text (Ollama) | phi4 / qwen3 (Ollama) | Development, privacy |
| `cloud` | text-embedding-3-small | gpt-4o | Frontier-model comparison |

Model switching and embedding dimensions are managed by rag-provider's `ModelRouter`. Each module connects via `rag.provider.url` (default `http://localhost:8086`).

### Strategy Pattern
Pluggable strategies via interfaces:
- `TextSplitter` - chunking strategies
- `EmbeddingModelPort` - embedding providers
- `Retriever` - retrieval strategies
- `VectorStorePort` - storage backends

### Repository Pattern
The `VectorStorePort` interface abstracts storage so PgVector, in-memory stores can be swapped transparently.

### Reactive/Parallel
- Java 21 virtual threads for blocking I/O (LLM/embedding calls)
- Project Reactor (Flux/Mono) for composable async
- Parallel processing for batch ingestion/embedding

## RAG Pipeline

### Ingestion Pipeline
```
Document → Parser → TextSplitter → EmbeddingModelPort → VectorStorePort
```

### Retrieval Pipeline
```
Query → QueryEmbedding → Retriever → ContextAssembly → LLM → Answer
```

### Observability (cross-cutting)
```
OpenTelemetry spans + Micrometer metrics + Structured logs
```

## Project Structure

```
apps/
├── rag-common/       # Shared library: domain, services, repositories
├── rag-provider/     # Centralized provider hub: model switching, chat/embed APIs
├── rag-basic/        # Basic RAG: fixed chunking, similarity search
├── rag-advanced/     # Advanced RAG: reranking, hybrid search, metadata
├── rag-agentic/      # Agentic RAG: tool calling, multi-step retrieval
├── rag-evaluation/   # Metrics, benchmarking, comparison
├── rag-observability/# Tracing, metrics, dashboards
├── rag-cli/          # Interactive CLI for testing queries
└── rag-tui/          # Terminal UI to add documents/webpages and chat
```

## Module Roles

| Module | Responsibility |
|--------|---------------|
| rag-common | Shared domain models, interfaces, configuration helpers |
| rag-provider | Centralized LLM/embedding clients, runtime model switching, provider profiles |
| rag-basic | Baseline RAG: fixed/recursive chunking, similarity search |
| rag-advanced | Reranking, hybrid search, semantic chunking, metadata filtering |
| rag-agentic | Agents, tool calling, multi-step retrieval, self-reflection |
| rag-evaluation | Metrics (precision, recall, MRR), benchmarking, comparison |
| rag-observability | Tracing, metrics, structured logging, dashboards |
| rag-cli | Interactive command-line testing of RAG queries |
| rag-tui | Terminal UI for interactive file/URL ingestion and chat |

## Module Comparison

| Module | Chunking | Retrieval | Extras |
|--------|----------|-----------|--------|
| rag-basic | Fixed, recursive, token | Vector similarity | Simple, baseline |
| rag-advanced | + Semantic | + Hybrid, reranking | Metadata filtering, query transform |
| rag-agentic | Agentic | Tool calling, multi-step | Self-reflection |
| rag-evaluation | - | - | Metrics, benchmarking, comparison |
| rag-provider | - | - | Chat/embed APIs, runtime model switching, model catalog |
| rag-tui | Recursive, Tika | Vector similarity | Interactive add-file / add-url + chat |

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
- [ADR-0010: rag-provider Service](decision-records/adr-0010-rag-provider.md)
- [ADR-0011: Model Catalog](decision-records/adr-0011-model-catalog.md)
- [ADR-0012: TUI connect Command](decision-records/adr-0012-tui-connect.md)
