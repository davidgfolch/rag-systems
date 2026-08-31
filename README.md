# RAG Systems

A monorepo for learning and comparing different RAG (Retrieval-Augmented Generation) implementations using the latest Java Spring ecosystem. Supports document ingestion (PDF, docs, web-sites) about any kind of knowledge - designed to be reusable and easily run on a regular local machine.

## Quick Start

```bash
# 1. Install dependencies (Windows)
.\scripts\install.bat

# 2. Start infrastructure (PostgreSQL + observability)
.\scripts\docker.bat up

# 3. Run a module
.\scripts\run.bat rag-basic
```

See [Getting Started](docs/guides/getting-started.md) for the full guide.

## Project Structure

```
apps/
├── rag-common/       # Shared library: domain, services, repositories
├── rag-basic/        # Basic RAG: fixed chunking, similarity search
├── rag-advanced/     # Advanced RAG: reranking, hybrid search, metadata
├── rag-agentic/      # Agentic RAG: tool calling, multi-step retrieval
├── rag-evaluation/   # Metrics, benchmarking, comparison
├── rag-observability/# Tracing, metrics, dashboards
└── rag-cli/          # Interactive CLI for testing queries
```

## Architecture Principles

The project follows TDD, DDD, SOLID, KISS, DRY, YAGNI, Composition over Inheritance, and Reactive/Parallel processing (when applicable). See [PLAN.md](PLAN.md) for the full architecture plan and [.claude/rules/architecture-guidelines.md](.claude/rules/architecture-guidelines.md) for agentic SDLC rules.

## Performance on a Local Machine

This project is designed to run **comfortably on a regular local machine**. RAG separates into layers with very different hardware demands, and the heavy compute is composable and optional. Full details in [docs/comparison/performance-metrics.md](docs/comparison/performance-metrics.md).

### Hardware Requirements

| Configuration | RAM | GPU | Typical Use |
|---------------|-----|-----|-------------|
| **Minimum** | 16GB | None | Embeddings + vector store + small LLM (3B-4B). Working but LLM is slow. |
| **Recommended** | 16GB | 8GB VRAM | Everything + Qwen3 8B for comfortable LLM generation |
| **Enthusiast** | 32GB+ | 16GB+ VRAM | Larger models, faster bulk indexing, agentic RAG |

### Layer Performance Breakdown (CPU)

| Layer | Model | Memory | Speed | Notes |
|-------|-------|--------|-------|-------|
| Embeddings | `nomic-embed-text` | ~300MB | ~580 chunks/sec | Great default, runs on CPU |
| Embeddings | `all-MiniLM-L6-v2` | ~90MB | ~100+ chunks/sec | Smallest, fastest |
| Vector Store | PgVector | - | Fast | Same machine PostgreSQL |
| Vector Store | SimpleVectorStore | - | Fastest | In-memory, small corpora |
| LLM | Qwen3 4B / Phi-4 | ~4GB | Usable | CPU-friendly |
| LLM | Qwen3 8B (Q4) | 4-8GB VRAM | Fast | Needs GPU |

### Key Takeaways

- **No GPU needed for retrieval** - Embeddings and vector search run great on CPU
- **GPU accelerates bulk indexing** - 5,000 pages in ~9 min CPU vs ~1 min GPU
- **LLM generation is the bottleneck on CPU** - use small models (3B-4B) or a GPU
- **Provider abstraction is essential** - swap OpenAI ↔ Ollama ↔ HuggingFace via config, not code

### Local Model Setup (Ollama)

```bash
ollama pull nomic-embed-text     # embeddings
ollama pull phi4                 # small CPU-friendly LLM
ollama pull qwen3:4b             # medium CPU-friendly LLM
ollama pull qwen3:8b             # GPU-friendly LLM (8GB VRAM+)
```

Run with the local profile:

```bash
.\scripts\run.bat rag-basic --profile local
```

## Scripts (Centralized Operations)

All operations are centralized in `scripts/` with Windows (`.bat`) and Linux/Mac (`.sh`) variants:

| Script | Purpose | Examples |
|--------|---------|----------|
| `install` | Install dependencies | `.\scripts\install.bat` |
| `test` | Run tests | `.\scripts\test.bat --coverage`, `.\scripts\test.bat rag-basic` |
| `build` | Build modules | `.\scripts\build.bat`, `.\scripts\build.bat rag-basic` |
| `run` | Run a module | `.\scripts\run.bat rag-basic --profile local` |
| `docker` | Docker operations | `.\scripts\docker.bat up`, `.\scripts\docker.bat up-obs` |

## RAG Modules

Each module is a **decoupled bounded context** implementing a different RAG architecture with Strategy patterns for chunking, embedding, and retrieval. This lets you compare approaches quantitatively.

| Module | Chunking | Retrieval | Extras |
|--------|----------|-----------|--------|
| rag-basic | Fixed, recursive, token | Vector similarity | Simple, baseline |
| rag-advanced | + Semantic | + Hybrid, reranking | Metadata filtering, query transform |
| rag-agentic | Agentic | Tool calling, multi-step | Self-reflection |
| rag-evaluation | - | - | Metrics, benchmarking, comparison |

## Observability

Every module emits tracing, metrics, and structured logs through the `rag-observability` module:

- **Tracing**: OpenTelemetry spans through the full query pipeline
- **Metrics**: Ingestion, retrieval, generation, and quality metrics with cost tracking
- **Dashboards**: Grafana (pipeline overview, retrieval performance, cost & usage)

See [docs/guides/observability.md](docs/guides/observability.md).

## Documentation

| Document | Purpose |
|----------|---------|
| [PLAN.md](PLAN.md) | Master architecture plan |
| [Getting Started](docs/guides/getting-started.md) | Setup and run guide |
| [Chunking Strategies](docs/guides/chunking-strategies.md) | Chunking approach comparison |
| [Vector Stores](docs/guides/vector-stores.md) | Vector store options |
| [Observability](docs/guides/observability.md) | Observability setup and dashboards |
| [Performance Metrics](docs/comparison/performance-metrics.md) | Quantitative comparison |
| [Trade-offs](docs/comparison/trade-offs.md) | Architecture trade-off analysis |

## Agentic SDLC

This repository is configured for agentic AI-assisted development compatible with both Claude and OpenCode:

- **Rules**: [.claude/rules/architecture-guidelines.md](.claude/rules/architecture-guidelines.md)
- **Skills**: [.claude/skills/](.claude/skills/) (test-implementer, architecture-tester, documentation-generator)
- **Config**: [.claude/CLAUDE.md](.claude/CLAUDE.md), [.opencode/opencode.json](.opencode/opencode.json)

## License

Learning project - no license restrictions on reuse.
