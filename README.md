# RAG Systems

[![CI](https://github.com/davidgfolch/rag-systems/actions/workflows/ci.yml/badge.svg)](https://github.com/davidgfolch/rag-systems/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-6DB33F?logo=spring)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-3.9-C71A36?logo=apache-maven)](https://maven.apache.org/)
<!-- COVERAGE_BADGES_START -->
![rag-common-core](https://img.shields.io/badge/rag--common--core-91%25-brightgreen)  ![rag-common-ingestion](https://img.shields.io/badge/rag--common--ingestion-96%25-brightgreen)  ![rag-common-retrieval](https://img.shields.io/badge/rag--common--retrieval-98%25-brightgreen)  ![rag-common-generation](https://img.shields.io/badge/rag--common--generation-95%25-brightgreen)  ![rag-basic](https://img.shields.io/badge/rag--basic-95%25-brightgreen)  ![rag-memory](https://img.shields.io/badge/rag--memory-99%25-brightgreen)  ![rag-webcrawler](https://img.shields.io/badge/rag--webcrawler-95%25-brightgreen)  ![rag-provider](https://img.shields.io/badge/rag--provider-97%25-brightgreen)  ![rag-advanced](https://img.shields.io/badge/rag--advanced-96%25-brightgreen)  ![rag-tui](https://img.shields.io/badge/rag--tui-90%25-brightgreen)
<!-- COVERAGE_BADGES_END -->

<!-- SONARQUBE_RESULTS_START -->
| Metric | Value |
|--------|-------|
| Quality Gate | OK |
| Bugs | 0 |
| Vulnerabilities | 0 |
| Security Hotspots | 0 |
| Code Smells | 0 |
| Coverage | 93.1% |
| Duplication | 0.0% |

*Last scan: 2026-09-22 14:59 UTC*
<!-- SONARQUBE_RESULTS_END -->

A monorepo for learning and comparing different RAG (Retrieval-Augmented Generation) implementations using Java Spring Boot and Spring AI. Each module is a decoupled bounded context with interchangeable chunking, embedding, and retrieval strategies — designed to be reusable across knowledge domains and to run comfortably on a regular local machine.

Modules range from basic vector similarity search to advanced hybrid retrieval with reranking, agentic tool-calling pipelines, and a terminal UI for interactive ingestion and Q&A. Full observability (tracing, metrics, dashboards) and benchmarking are built in for quantitative comparison.

## Prerequisites

- **Java 25** (LTS)
- **Maven 3.9+**
- **Docker** (for PostgreSQL/PgVector)
- **Ollama** (recommended, for local models) — or an OpenAI-compatible API key (e.g. [OpenRouter](https://openrouter.ai)) as an alternative

## Installation

```bash
# Pull local models (essential)
ollama pull nomic-embed-text    # embeddings
ollama pull phi4                # small CPU-friendly LLM

# Install project dependencies (Windows)
.\scripts\install.bat
# Linux/Mac
./scripts/install.sh

# Start PostgreSQL (Windows)
.\scripts\docker.bat up
# Linux/Mac
./scripts/docker.sh up
```

Ollama runs natively, containerized (`.\scripts\docker.bat up-ollama`), or points at an externally running instance via `OLLAMA_BASE_URL` — with `INFRA_MODE=auto` (default), `up-ollama` reuses an already-reachable Ollama instead of starting the `rag-ollama` container. See the [Getting Started guide](docs/guides/getting-started.md).

Prefer Ollama for zero-cost, private local development. When you need frontier models without local GPUs, register OpenRouter (or any OpenAI-compatible endpoint) with rag-provider — at runtime via the TUI `connect` command, no restart needed.

## Build

```bash
# Build all modules (Windows)
.\scripts\build.bat
# Build a specific module
.\scripts\build.bat rag-tui
```

## Quick Start

All RAG modules depend on **rag-provider** for LLM and embedding compute. Start it first:

```bash
# Start rag-provider (Windows)
.\scripts\run.bat rag-provider --profile local
# Linux/Mac
./scripts/run.sh rag-provider --profile local
```

Then start the TUI (Terminal UI) — it lets you add local files or web pages as sources and then ask grounded questions against them:

```bash
# Start the TUI with local models (Windows; rag-tui is the default)
.\scripts\run.bat --profile local
# Linux/Mac
./scripts/run.sh --profile local
```

Once started, use these commands interactively:

```
add-file /path/to/document.pdf    # ingest a local file
add-url https://example.com       # ingest a web page
ask What is this document about?  # ask a question against ingested content
exit                              # quit
```

The pipeline: files and URLs are parsed, split into chunks, embedded, and stored in a vector store. Questions retrieve the most relevant chunks and generate a grounded answer via the configured LLM.

Models default to **Ollama** (phi4 chat, nomic-embed-text embeddings). Switch chat/embedding providers at runtime — no restart — with the TUI `connect` command (e.g. `connect chat openrouter gpt-4o`), or via the rag-provider HTTP API.

See the [TUI Guide](docs/guides/tui-ingestion.md) for details on extending sources and how the architecture works.

### Run a RAG module directly (HTTP API)

Prefer the raw REST API over the TUI? Start any runnable module the same way — **rag-provider must be running first**. Each module is an independent Spring Boot app exposing the shared OpenAPI contract on its own port:

```bash
# Windows
.\scripts\run.bat rag-basic --profile local        # http://localhost:8081
.\scripts\run.bat rag-advanced --profile local     # http://localhost:8082

# Linux/Mac
./scripts/run.sh rag-basic --profile local
./scripts/run.sh rag-advanced --profile local
```

Quick smoke test against rag-basic:

```bash
# Ingest a local file (multipart form field "file")
curl -F "file=@docs/report.pdf" http://localhost:8081/api/documents/ingest-file

# Ask a grounded question
curl -H "Content-Type: application/json" \
  -d '{"question":"What is this document about?","topK":4}' \
  http://localhost:8081/api/query
```

The API contract — every endpoint, payload, and WebSocket frame — is defined **once** in [rag-api.yaml](apps/rag-contract/src/main/resources/openapi/rag-api.yaml). There is no per-module Swagger UI; use that YAML (or the Swagger editor linked below) as your API reference. See [Quick access URLs](#quick-access-urls) for every service endpoint.

### Quick access URLs

| Service | URL | Health | Metrics |
|---------|-----|--------|---------|
| rag-provider (LLM/embedding hub) | <http://localhost:8086> | `/actuator/health` | `/actuator/prometheus` |
| rag-basic | <http://localhost:8081> | `/actuator/health` | `/actuator/prometheus` |
| rag-advanced | <http://localhost:8082> | `/actuator/health` | `/actuator/prometheus` |
| rag-memory (conversation history) | <http://localhost:8084> | `/actuator/health` | `/actuator/prometheus` |
| rag-webcrawler (web fetching tool) | <http://localhost:8085> | `/actuator/health` | `/actuator/prometheus` |
| Grafana dashboards | <http://localhost:3000> (`admin`/`admin`) | - | - |
| Prometheus metrics | <http://localhost:9090> | - | - |
| Tempo traces | <http://localhost:3200> | - | - |
| Loki logs | <http://localhost:3100> | - | - |
| OpenAPI spec (API reference) | [rag-api.yaml](apps/rag-contract/src/main/resources/openapi/rag-api.yaml) | [View in Swagger editor](https://editor.swagger.io/?url=https://raw.githubusercontent.com/davidgfolch/rag-systems/main/apps/rag-contract/src/main/resources/openapi/rag-api.yaml) | - |

Grafana/Prometheus/Tempo/Loki are only available after `.\scripts\docker.bat up-obs` (see the [Observability guide](docs/guides/observability.md)).

## Comparing the modules

Each `rag-*` implementation answers the same questions over the same corpus, so you can compare retrieval quality and cost side by side:

1. **Ingest the same corpus into both** — run rag-basic and rag-advanced, then `curl -F "file=@..."` the same documents into each (data stays isolated per PgVector schema).
2. **Drive identical queries** — the same `{"question": ...}` to both `/api/query` endpoints.
3. **Measure quantitatively** — turn on the `observability` profile and compare Grafana dashboards, Prometheus metrics, and trace timelines (see [rag-observability](apps/rag-observability/README.md)), or run the [rag-evaluation](apps/rag-evaluation/README.md) benchmark harness and the [performance metrics](docs/comparison/performance-metrics.md).

## Modules & Strategy

The monorepo is organized around a **thin TUI + switchable RAG modules**. Each `rag-*` module is an independent Spring Boot application exposing the [shared OpenAPI contract](apps/rag-contract/src/main/resources/openapi/rag-api.yaml); the TUI starts/stops them and routes work to the active one.

| Module | Role | Data | Default port | README |
|--------|------|------|--------------|--------|
| **rag-contract** | OpenAPI spec + generated DTOs | - | - | [README](apps/rag-contract/README.md) |
| **rag-common-core** | Kernel: domain models, strategy ports, tracing | - | - | [README](apps/rag-common-core/README.md) |
| **rag-common-ingestion** | Chunking, parsing, ingestion services | - | - | [README](apps/rag-common-ingestion/README.md) |
| **rag-common-retrieval** | In-memory + PgVector store implementations | - | - | [README](apps/rag-common-retrieval/README.md) |
| **rag-common-generation** | Chat/embedding adapters, generation service | - | - | [README](apps/rag-common-generation/README.md) |
| **rag-provider** | Centralized LLM/embedding provider service | - | 8086 | [README](apps/rag-provider/README.md) |
| **rag-basic** | Basic RAG | schema `rag_basic` | 8081 | [README](apps/rag-basic/README.md) |
| **rag-advanced** | Advanced RAG (reranking, hybrid) | schema `rag_advanced` | 8082 | [README](apps/rag-advanced/README.md) |
| **rag-agentic** *(planned)* | Agentic RAG (tool calling) | schema `rag_agentic` | 8083 | [README](apps/rag-agentic/README.md) |
| **rag-memory** | Conversation history (non-vector) | schema `rag_memory` | 8084 | [README](apps/rag-memory/README.md) |
| **rag-webcrawler** | Intelligent web fetching tool | - | 8085 | [README](apps/rag-webcrawler/README.md) |
| **rag-tui** | Thin interface + control plane | - | *(non-web)* | [README](apps/rag-tui/README.md) |

> Ports are the defaults in each module's `application.yml`, each read from a per-module `RAG_*_PORT` env var (e.g. `RAG_BASIC_PORT`, `RAG_PROVIDER_PORT`) declared in the root `.env`. Keep each `RAG_*_PORT` in sync with the port in the matching `RAG_*_URL`. See the [Quick access URLs](#quick-access-urls) table.

### Module Dependencies

Compile-time (Maven) and runtime (HTTP/WebSocket) interactions between modules:

```mermaid
graph TB
    subgraph Foundation
        contract[rag-contract<br/>OpenAPI DTOs]
        common[rag-common-*<br/>Shared strategies]
        obs[rag-observability<br/>Tracing & metrics]
    end

    subgraph Runtime Services
        provider[rag-provider<br/>LLM & embedding service]
        memory[rag-memory<br/>Conversation history]
        webcrawler[rag-webcrawler<br/>Web fetching tool]
    end

    subgraph RAG Implementations
        basic[rag-basic<br/>Basic RAG]
        advanced[rag-advanced<br/>Advanced RAG]
        agentic[rag-agentic<br/>Agentic RAG]
    end

    subgraph Interfaces
        tui[rag-tui<br/>Terminal UI + control plane]
        cli[rag-cli<br/>CLI tool (placeholder)]
    end

    subgraph Tooling
        evaluation[rag-evaluation<br/>Benchmarking]
    end

    %% Maven compile-time dependencies
    common --> contract
    obs --> common
    basic --> common
    basic --> contract
    basic --> obs
    advanced --> common
    advanced --> obs
    agentic --> common
    agentic --> obs
    tui --> common
    tui --> contract
    memory --> contract
    webcrawler --> common
    webcrawler --> contract
    provider --> contract
    provider --> obs
    evaluation --> common
    cli --> common

    %% Runtime HTTP/WebSocket interactions (dashed)
    tui -.->|start/stop + route requests| basic
    tui -.->|start/stop + route requests| advanced
    tui -.->|start/stop + route requests| agentic
    tui -.->|conversation history| memory
    tui -.->|add-url crawling| webcrawler
    basic -.->|LLM + embeddings| provider
    advanced -.->|LLM + embeddings| provider
    agentic -.->|LLM + embeddings| provider
    basic -.->|web content| webcrawler
    evaluation -.->|benchmark against| basic
    evaluation -.->|benchmark against| advanced
    evaluation -.->|benchmark against| agentic
    cli -.->|queries| common
```

**Legend:** solid arrows = Maven compile-time dependency, dashed arrows = runtime HTTP/WebSocket calls.

**Data isolation:** each rag-* implementation stores vectors in its own PostgreSQL schema (`rag_basic`, `rag_advanced`, ...) in a single `chunks` table. Per-document query scoping is done through the `documentId` chunk metadata key written by `PgVectorStoreAdapter` (see [ADR-0006](docs/architecture/decision-records/adr-0006-data-store-isolation.md)) - no cross-module contamination. Conversation state lives separately in `rag_memory`.

**Module switching:** use the TUI commands `modules`, `start <module>`, `stop <module>`, and `use <module>`. Chat streams over WebSocket `/ws/chat` so an in-flight answer can be cancelled. `add-url` is module-orchestrated: the active rag-module calls `rag-webcrawler`, chunks with its own strategy, stores, and notifies the TUI.

See the architecture decisions for the full rationale:
- [ADR-0005: API-First Contract](docs/architecture/decision-records/adr-0005-api-contract.md)
- [ADR-0006: Data Store Isolation](docs/architecture/decision-records/adr-0006-data-store-isolation.md)
- [ADR-0007: Thin TUI + Module Control Plane](docs/architecture/decision-records/adr-0007-tui-interface.md)
- [ADR-0008: rag-memory Module](docs/architecture/decision-records/adr-0008-rag-memory.md)
- [ADR-0009: rag-webcrawler Module](docs/architecture/decision-records/adr-0009-rag-webcrawler.md)
- [ADR-0010: rag-provider Service](docs/architecture/decision-records/adr-0010-rag-provider.md)

## More Documentation

| Topic | Link |
|-------|------|
| Architecture Overview | [docs/architecture/](docs/architecture/) |
| Master Plan | [docs/PLAN.md](docs/PLAN.md) |
| All Guides | [docs/guides/](docs/guides/) |
| Performance & Benchmarks | [docs/comparison/](docs/comparison/) |
| Observability (tracing, metrics, dashboards) | [docs/guides/observability.md](docs/guides/observability.md) |
| SonarQube Static Analysis | [docs/guides/sonarqube.md](docs/guides/sonarqube.md) |
| Module readmes | [apps/](apps/) — per-module guides under `apps/*/README.md` |
| Agentic SDLC (rules, skills, config) | [.claude/](.claude/) |

## License

Learning project — no license restrictions on reuse.
