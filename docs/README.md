# Documentation

All project documentation lives here. The main [README](../README.md) covers prerequisites, installation, build, and the TUI quick start.

## Documentation Map

| Section | Description |
|---------|-------------|
| [Architecture](architecture/) | Layered architecture, module roles, design decisions |
| [Guides](guides/) | Step-by-step setup and usage guides |
| [Comparison](comparison/) | Performance metrics, benchmarks, trade-off analysis |
| [Process](process/) | Development process and conventions (ASDLC) |
| [Master Plan](PLAN.md) | Full architecture plan, phases, and success criteria |

## Module Readmes

Each module has its own README under `apps/*/README.md` — see the [module table](../README.md#modules--strategy) for links, roles, and default ports.

| Module | Role |
|--------|------|
| [rag-contract](../apps/rag-contract/README.md) | OpenAPI spec + DTOs, shared constants |
| [rag-common-core](../apps/rag-common-core/README.md) | Kernel: domain, ports, tracing |
| [rag-common-ingestion](../apps/rag-common-ingestion/README.md) | Parsers + chunkers + ingestion services |
| [rag-common-retrieval](../apps/rag-common-retrieval/README.md) | PgVector / in-memory stores |
| [rag-common-generation](../apps/rag-common-generation/README.md) | Chat/embedding adapters, ChatService |
| [rag-common-app](../apps/rag-common-app/README.md) | Shared pipeline wiring for the rag-* runtimes |
| [rag-provider](../apps/rag-provider/README.md) | LLM/embedding provider hub (**8086**) |
| [rag-basic](../apps/rag-basic/README.md) | Baseline RAG (**8081**) |
| [rag-advanced](../apps/rag-advanced/README.md) | Reranking, hybrid, metadata filtering (**8082**) |
| [rag-agentic](../apps/rag-agentic/README.md) | Query planning, tool calling, multi-step retrieval (**8083**) |
| [rag-memory](../apps/rag-memory/README.md) | Conversation history (**8084**) |
| [rag-webcrawler](../apps/rag-webcrawler/README.md) | Web fetching tool (**8085**) |
| [rag-observability](../apps/rag-observability/README.md) | Tracing, metrics, dashboards |
| [rag-evaluation](../apps/rag-evaluation/README.md) | Benchmarking — *planned* |
| [rag-cli](../apps/rag-cli/README.md) | CLI client — *placeholder* |
| [rag-tui](../apps/rag-tui/README.md) | Terminal UI + control plane (non-web) |

## Guides

| Guide | Purpose |
|-------|---------|
| [Getting Started](guides/getting-started.md) | Prerequisites, installation, configuration profiles |
| [Chunking Strategies](guides/chunking-strategies.md) | Fixed, recursive, semantic, and agentic chunking comparison |
| [Vector Stores](guides/vector-stores.md) | PgVector, SimpleVectorStore, and provider options |
| [Observability](guides/observability.md) | OpenTelemetry tracing, Micrometer metrics, Grafana dashboards |
| [TUI Ingestion](guides/tui-ingestion.md) | Terminal UI for adding files/web pages and asking questions |
| [SonarQube](guides/sonarqube.md) | Static analysis setup and quality gate |
| [CI Workflow](guides/ci-workflow.md) | GitHub Actions CI and badge setup |

## Architecture

| Document | Purpose |
|----------|---------|
| [Overview](architecture/overview.md) | Layered architecture, module roles, pipeline diagrams |
| [Decision Records](architecture/decision-records/) | Architecture Decision Records (ADRs) |

## Comparison

| Document | Purpose |
|----------|---------|
| [Performance Metrics](comparison/performance-metrics.md) | Quantitative benchmarks per layer and module |
| [Trade-offs](comparison/trade-offs.md) | Architecture trade-off analysis |
