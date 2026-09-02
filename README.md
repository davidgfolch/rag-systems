# RAG Systems

[![CI](https://github.com/davidgfolch/rag-systems/actions/workflows/ci.yml/badge.svg)](https://github.com/davidgfolch/rag-systems/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-6DB33F?logo=spring)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-3.9-C71A36?logo=apache-maven)](https://maven.apache.org/)

A monorepo for learning and comparing different RAG (Retrieval-Augmented Generation) implementations using Java Spring Boot and Spring AI. Each module is a decoupled bounded context with interchangeable chunking, embedding, and retrieval strategies — designed to be reusable across knowledge domains and to run comfortably on a regular local machine.

Modules range from basic vector similarity search to advanced hybrid retrieval with reranking, agentic tool-calling pipelines, and a terminal UI for interactive ingestion and Q&A. Full observability (tracing, metrics, dashboards) and benchmarking are built in for quantitative comparison.

## Prerequisites

- **Java 21** (LTS)
- **Maven 3.9+**
- **Docker** (for PostgreSQL/PgVector)
- **Ollama** (optional, for local models)

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

## Build

```bash
# Build all modules (Windows)
.\scripts\build.bat

# Build a specific module
.\scripts\build.bat rag-tui
```

## Quick Start

The fastest way to try the system is with the **TUI** (Terminal UI) — it lets you add local files or web pages as sources and then ask grounded questions against them.

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

See the [TUI Guide](docs/guides/tui-ingestion.md) for details on extending sources and how the architecture works.

## Modules & Strategy

The monorepo is organized around a **thin TUI + switchable RAG modules**. Each `rag-*` module is an independent Spring Boot application exposing the [shared OpenAPI contract](apps/rag-contract/src/main/resources/openapi/rag-api.yaml); the TUI starts/stops them and routes work to the active one.

| Module | Role | Data | Port (from `.env`) |
|--------|------|------|--------------------|
| **rag-contract** | OpenAPI spec + generated DTOs | - | - |
| **rag-common** | Shared strategies (chunking, parsing, adapters, ingestion) | - | - |
| **rag-basic** | Basic RAG | schema `rag_basic` | `RAG_BASIC_URL` |
| **rag-advanced** *(planned)* | Advanced RAG (reranking, hybrid) | schema `rag_advanced` | `RAG_ADVANCED_URL` |
| **rag-agentic** *(planned)* | Agentic RAG (tool calling) | schema `rag_agentic` | `RAG_AGENTIC_URL` |
| **rag-memory** | Conversation history (non-vector) | schema `rag_memory` | `RAG_MEMORY_URL` |
| **rag-webcrawler** | Intelligent web fetching tool | - | `RAG_WEBCRAWLER_URL` |
| **rag-tui** | Thin interface + control plane | - | `RAG_TUI_URL` |

**Data isolation:** each rag-* implementation stores vectors in its own PostgreSQL schema (`rag_basic`, `rag_advanced`, ...) in a single `chunks` table with a `document_id` column - no cross-module contamination. Conversation state lives separately in `rag_memory`.

**Module switching:** use the TUI commands `modules`, `start <module>`, `stop <module>`, and `use <module>`. Chat streams over WebSocket `/ws/chat` so an in-flight answer can be cancelled. `add-url` is module-orchestrated: the active rag-module calls `rag-webcrawler`, chunks with its own strategy, stores, and notifies the TUI.

See the architecture decisions for the full rationale:
- [ADR-0005: API-First Contract](docs/architecture/decision-records/adr-0005-api-contract.md)
- [ADR-0006: Data Store Isolation](docs/architecture/decision-records/adr-0006-data-store-isolation.md)
- [ADR-0007: Thin TUI + Module Control Plane](docs/architecture/decision-records/adr-0007-tui-interface.md)
- [ADR-0008: rag-memory Module](docs/architecture/decision-records/adr-0008-rag-memory.md)
- [ADR-0009: rag-webcrawler Module](docs/architecture/decision-records/adr-0009-rag-webcrawler.md)

## More Documentation

| Topic | Link |
|-------|------|
| Architecture Overview | [docs/architecture/](docs/architecture/) |
| Master Plan | [docs/PLAN.md](docs/PLAN.md) |
| All Guides | [docs/guides/](docs/guides/) |
| Performance & Benchmarks | [docs/comparison/](docs/comparison/) |
| Observability (tracing, metrics, dashboards) | [docs/guides/observability.md](docs/guides/observability.md) |
| SonarQube Static Analysis | [docs/guides/sonarqube.md](docs/guides/sonarqube.md) |
| Agentic SDLC (rules, skills, config) | [.claude/](.claude/) |

## License

Learning project — no license restrictions on reuse.
