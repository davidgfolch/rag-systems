# Getting Started with RAG Systems

## Prerequisites

- **Java 21** (LTS)
- **Maven 3.9+**
- **Docker** (for PostgreSQL + observability)
- **Ollama** (for local models) - optional but recommended for local development
- **16GB RAM** recommended (8GB minimum for embeddings-only)

## 1. Install Ollama Models (Local Profile)

```bash
# Embedding model (essential)
ollama pull nomic-embed-text

# Small CPU-friendly LLM
ollama pull phi4

# GPU-friendly LLM (8GB VRAM+)
ollama pull qwen3:8b
```

Verify:
```bash
ollama list
```

## 2. Configure Environment

Environment files are bootstrapped automatically. Every operational script
(`docker`, `run`, `sonar`, `install`, `build`, `test`) runs
`scripts/bootstrap-env.{bat,sh}`, which copies the examples from `scripts/`

- `scripts/.env.example` → `.env` (non-secret configuration)
- `scripts/.env.secrets.example` → `.env.secrets` (secrets: API keys, passwords, tokens)

to the repo root **only if they don't already exist**, and generates random
passwords for any blank `PGVECTOR_PASSWORD` and `SONAR_ADMIN_PASSWORD`
(both in `.env.secrets`). The generated files are gitignored.

So no manual copy is needed — just run any script, then edit `.env` if you want
to override defaults (e.g. profile), and edit `.env.secrets` for any secrets
(e.g. cloud API keys). Defaults point to Ollama local models.

## 3. Install Dependencies

```bash
# Windows
.\scripts\install.bat

# Linux/Mac
./scripts/install.sh
```

## 4. Start Infrastructure

Start PostgreSQL (PgVector):

```bash
# Windows
.\scripts\docker.bat up

# Linux/Mac
./scripts/docker.sh up
```

Optionally start observability (Prometheus + Grafana):

```bash
# Windows
.\scripts\docker.bat up-obs

# Linux/Mac
./scripts/docker.sh up-obs
```

## 5. Start rag-provider

All RAG modules (rag-basic, rag-advanced, etc.) depend on **rag-provider** for LLM and embedding compute. It must be running before any other module:

```bash
# Windows
.\scripts\run.bat rag-provider --profile local

# Linux/Mac
./scripts/run.sh rag-provider --profile local
```

rag-provider starts on port **8086** by default. It manages Ollama/OpenAI clients centrally and exposes them over HTTP so downstream modules don't need direct provider dependencies.

> **Important — embedding dimension changes:** each embedding model produces vectors of a fixed dimension (e.g. `nomic-embed-text` → 768, `text-embedding-3-small` → 1536). When you switch the active embedding model (via `POST /api/provider/embedding` or the TUI `connect embedding` command), any documents already embedded with the previous model remain stored at the old dimension. rag-provider re-detects the new dimension lazily (at switch time and on each status read), but the vector store still holds the old vectors — similarity search and retrieval will fail with dimension-mismatch errors until you **re-ingest your documents** with the new embedding model. Keep to one embedding model per corpus, or clear and re-ingest after a switch.

See the [rag-provider README](../../apps/rag-provider/README.md) for full configuration, API reference, and how to connect external providers.

## 6. Run a Module

Run rag-basic with the local (Ollama) profile:

```bash
# Windows
.\scripts\run.bat rag-basic --profile local

# Linux/Mac
./scripts/run.sh rag-basic --profile local
```

The API starts on http://localhost:8080 with Swagger UI at http://localhost:8080/swagger-ui.html

## 7. Test with the CLI

Use the interactive CLI to query without a web UI:

```bash
# Windows
.\scripts\run.bat rag-cli --profile local

# Linux/Mac
./scripts/run.sh rag-cli --profile local
```

## 8. Run Tests

```bash
# Run all tests with coverage
.\scripts\test.bat --coverage

# Run a specific module
.\scripts\test.bat rag-basic
```

## 9. Run SonarQube Static Analysis (Optional)

Start the local SonarQube server and analyze the whole monorepo (bugs, code smells, coverage, quality gate):

```bash
# Windows
.\scripts\sonar.bat up-scan %SONAR_TOKEN%

# Linux/Mac
./scripts/sonar.sh up-scan $SONAR_TOKEN
```

A token is only needed the first time (generate one at http://localhost:9000 → **My Account → Security**, default login `admin`/`admin`). Results are at http://localhost:9000 under project `com.rag:rag-systems`. See the [SonarQube guide](../guides/sonarqube.md).

## Scripts Reference

All operations are centralized in `scripts/` with Windows (`.bat`) and Linux/Mac (`.sh`) variants:

| Script | Purpose | Examples |
|--------|---------|----------|
| `install` | Install dependencies | `.\scripts\install.bat` |
| `build` | Build modules | `.\scripts\build.bat`, `.\scripts\build.bat rag-basic` |
| `test` | Run tests | `.\scripts\test.bat --coverage`, `.\scripts\test.bat rag-basic` |
| `run` | Run a module | `.\scripts\run.bat rag-basic --profile local`, `.\scripts\run.bat rag-tui --profile local` |
| `docker` | Docker operations | `.\scripts\docker.bat up`, `.\scripts\docker.bat up-obs`, `.\scripts\docker.bat up-sonar` |
| `sonar` | SonarQube analysis | `.\scripts\sonar.bat up-scan %SONAR_TOKEN%` |

## Configuration Profiles

| Profile | Description |
|---------|-------------|
| `local` | Ollama models (nomic-embed-text, phi4) - default |
| `cloud` | OpenAI models (text-embedding-3-small, gpt-4o) |
| `observability` | Enables OpenTelemetry, Prometheus, Grafana |

Set via `SPRING_PROFILES_ACTIVE` env var or `--profile` script flag.

## Troubleshooting

**Cannot connect to Ollama (port 11434)**
- Ensure Ollama is running: `ollama serve` or check system service

**Slow LLM generation**
- Use a smaller model (phi4 instead of qwen3:8b) on CPU
- Add a GPU for comfortable generation

**Slow bulk embedding**
- This is normal on first batch (model loading). For large corpora on CPU, use `nomic-embed-text` which is CPU-optimized.

**PostgreSQL not connecting**
- Ensure Docker is running and check `docker compose ps`

**Embedding dimension mismatch after switching models**
- Vectors stored under the old embedding model keep its dimension; re-ingest your documents with the new active embedding model (see the warning in [Start rag-provider](#5-start-rag-provider))
- Check the active dimension with `GET http://localhost:8086/api/provider`

**rag-provider unreachable**
- Ensure it is running: `.\scripts\run.bat rag-provider --profile local`
- Downstream modules fail with connection errors until rag-provider is up

## Next Steps

- Read the [Architecture Overview](../architecture/overview.md)
- Explore [Chunking Strategies](../guides/chunking-strategies.md)
- Compare [Performance Metrics](../comparison/performance-metrics.md)
- Set up [SonarQube Static Analysis](../guides/sonarqube.md)
