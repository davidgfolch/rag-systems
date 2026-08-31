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

Copy the environment template:

```bash
# Windows
copy .env.example .env

# Linux/Mac
cp .env.example .env
```

Edit `.env` to match your setup. Defaults point to Ollama local models.

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

# Run only architecture tests
.\scripts\test.bat --architecture

# Run a specific module
.\scripts\test.bat rag-basic
```

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
