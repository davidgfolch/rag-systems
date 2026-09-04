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

- `scripts/.env.example` → `.env`
- `scripts/.env.secrets.example` → `.env.secrets`

to the repo root **only if they don't already exist**, and generates random
passwords for any blank `PGVECTOR_PASSWORD` (`.env`) and
`SONAR_ADMIN_PASSWORD` (`.env.secrets`). The generated files are gitignored.

So no manual copy is needed — just run any script, then edit `.env` if you want
to override defaults (e.g. cloud API keys). Defaults point to Ollama local models.

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

## 5. Run a Module

Run rag-basic with the local (Ollama) profile:

```bash
# Windows
.\scripts\run.bat rag-basic --profile local

# Linux/Mac
./scripts/run.sh rag-basic --profile local
```

The API starts on http://localhost:8080 with Swagger UI at http://localhost:8080/swagger-ui.html

## 6. Test with the CLI

Use the interactive CLI to query without a web UI:

```bash
# Windows
.\scripts\run.bat rag-cli --profile local

# Linux/Mac
./scripts/run.sh rag-cli --profile local
```

## 7. Run Tests

```bash
# Run all tests with coverage
.\scripts\test.bat --coverage

# Run a specific module
.\scripts\test.bat rag-basic
```

## 8. Run SonarQube Static Analysis (Optional)

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

## Next Steps

- Read the [Architecture Overview](../architecture/overview.md)
- Explore [Chunking Strategies](../guides/chunking-strategies.md)
- Compare [Performance Metrics](../comparison/performance-metrics.md)
- Set up [SonarQube Static Analysis](../guides/sonarqube.md)
