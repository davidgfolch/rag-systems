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
# Start the TUI with local models (Windows)
.\scripts\run.bat rag-tui --profile local

# Linux/Mac
./scripts/run.sh rag-tui --profile local
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
