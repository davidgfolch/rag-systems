# rag-provider

Dedicated Spring Boot service owning all LLM and embedding provider clients (Ollama, OpenAI, OpenAI-compatible). It is the **provider hub** of the monorepo: every other RAG module gets its chat and embedding compute from here over HTTP, so downstream modules keep no provider dependencies of their own.

Port: **8086** (override with `RAG_PROVIDER_PORT`).

## Why it is a required dependency

All RAG modules (rag-basic, rag-tui, future rag-advanced/rag-agentic) connect to rag-provider for model access. It must be running before any of them:

- **Single source of provider clients** — Ollama/OpenAI clients are built and managed in one place; modules talk to it through thin `rag-common` bridges (`RemoteChatModelPort`, `RemoteEmbeddingModel`).
- **Runtime model switching** — the active chat/embedding model can be swapped with an HTTP call, no restart and no config-profile change.
- **Provider profiles survive restarts** — providers added at runtime (e.g. a custom OpenAI-compatible endpoint) are persisted to `data/provider-profiles.json`.
- **Lazy embedding dimension detection** — the active embedding dimension is cached and only resolved on demand; switching models resets it.

Consumer-side configuration (in each rag-* module): `rag.provider.url` (default `http://localhost:8086`).

## How to start it

Requires the project to be installed once (`.\scripts\install.bat` / `./scripts/install.sh`) and Ollama running for the `local` profile:

```bash
# Windows
.\scripts\run.bat rag-provider --profile local

# Linux/Mac
./scripts/run.sh rag-provider --profile local
```

Verify:

```bash
curl http://localhost:8086/api/provider
```

```json
{
  "chat": { "providerId": "ollama", "model": "phi4" },
  "embedding": { "providerId": "ollama", "model": "nomic-embed-text" },
  "embeddingDimension": 768
}
```

## Configuration

All properties are overridable via environment variables.

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `server.port` | `RAG_PROVIDER_PORT` | `8086` | HTTP port |
| `rag.provider.ollama.base-url` | `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL |
| `rag.provider.ollama.chat-model` | `OLLAMA_CHAT_MODEL` | `phi4` | Default Ollama chat model |
| `rag.provider.ollama.embedding-model` | `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text` | Default Ollama embedding model |
| `rag.provider.openai.api-key` | `OPENAI_API_KEY` | *(empty)* | OpenAI API key |
| `rag.provider.openai.base-url` | `OPENAI_BASE_URL` | *(empty)* | OpenAI base URL override |
| `rag.provider.openai.chat-model` | `OPENAI_CHAT_MODEL` | `gpt-4o` | Default OpenAI chat model |
| `rag.provider.openai.embedding-model` | `OPENAI_EMBEDDING_MODEL` | `text-embedding-3-small` | Default OpenAI embedding model |
| `rag.provider.defaults.chat-provider` | `DEFAULT_CHAT_PROVIDER` | `ollama` | Active chat provider at boot (profile id) |
| `rag.provider.defaults.embedding-provider` | `DEFAULT_EMBEDDING_PROVIDER` | `ollama` | Active embedding provider at boot (profile id) |
| `rag.provider.profiles-file` | — | `data/provider-profiles.json` | JSON file holding persisted provider profiles |
| `rag.provider.catalog.url` | `MODEL_CATALOG_URL` | `https://models.dev/api.json` | Model catalog endpoint |
| `rag.provider.catalog.ttl` | `MODEL_CATALOG_TTL` | `86400` | Catalog cache TTL in seconds |
| `rag.provider.catalog.enabled` | `MODEL_CATALOG_ENABLED` | `true` | Enable catalog fetching |

## HTTP API

### Status & control

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET` | `/api/provider` | Current status: active chat/embedding models + embedding dimension |
| `POST` | `/api/provider/chat` | Switch active chat model (`{"providerId": "openai", "model": "gpt-4o"}`) |
| `POST` | `/api/provider/embedding` | Switch active embedding model |
| `POST` | `/api/provider/configure` | Upsert a provider profile |

### Compute

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `POST` | `/api/complete` | Non-streaming chat completion |
| `POST` | `/api/chat/stream` | Streaming chat (SSE) |
| `POST` | `/api/embed` | Embedding computation |

### Model catalog

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET` | `/api/provider/catalog` | Browse the model catalog (from models.dev, cached) |
| `POST` | `/api/provider/catalog/refresh` | Force a catalog refresh |

## Connecting external providers

Providers can be registered in three ways, and any OpenAI-compatible endpoint works with no extra code.

### 1. Edit `data/provider-profiles.json`

Add an entry with type `OPENAI_COMPATIBLE` (or `OPENAI`) and restart (or reconfigure at runtime):

```json
{
  "id": "my-custom-provider",
  "type": "OPENAI_COMPATIBLE",
  "displayName": "My Custom Provider",
  "baseUrl": "https://my-api.example.com/v1",
  "apiKey": "sk-..."
}
```

> The `baseUrl` is stripped of a trailing `/v1` automatically, since the Spring AI OpenAI client appends it internally. API keys are stored in this file — keep it out of version control.

### 2. Runtime HTTP API

Register without restarting and switch to it immediately:

```bash
curl -X POST http://localhost:8086/api/provider/configure \
  -H "Content-Type: application/json" \
  -d '{"providerId":"my-custom-provider","type":"OPENAI_COMPATIBLE","displayName":"My Custom Provider","baseUrl":"https://my-api.example.com/v1","apiKey":"sk-..."}'
```

Then switch the chat model:

```bash
curl -X POST http://localhost:8086/api/provider/chat \
  -H "Content-Type: application/json" \
  -d '{"providerId":"my-custom-provider","model":"gpt-4o"}'
```

Runtime-registered providers are persisted and survive restarts.

### 3. Via the TUI

From the TUI, `connect` command family talks to rag-provider directly (requires `rag.provider.url` in the TUI, default `http://localhost:8086`):

```
connect                             # current status (models + dimension + catalog)
connect catalog                     # browse the model catalog
connect chat openrouter gpt-4o      # switch the chat model (registers unknown providers interactively)
connect embedding openrouter ...    # switch the embedding model
connect refresh                     # force catalog refresh
```

### 4. Environment variables (seed defaults)

Ollama/OpenAI defaults are seeded at boot from `OLLAMA_*`, `OPENAI_*`, `DEFAULT_CHAT_PROVIDER` and `DEFAULT_EMBEDDING_PROVIDER` (see the configuration table).

## Provider types

| Type | Behavior |
|------|----------|
| `OLLAMA` | Ollama chat/embedding clients via the configured `baseUrl` (`OllamaApi`) |
| `OPENAI` | OpenAI clients via `api.openai.com` (or `baseUrl` override) |
| `OPENAI_COMPATIBLE` | Same OpenAI clients pointed at an arbitrary `baseUrl` (OpenRouter, GLHF, any OpenAI-shaped API) |

## Embedding dimension changes

Each embedding model produces vectors of a **fixed dimension** (e.g. `nomic-embed-text` → 768, `text-embedding-3-small` → 1536). The active dimension is resolved lazily from the embedding client (and re-detected after every switch), then reported via `GET /api/provider`.

> **Warning:** switching the embedding model changes the dimension for new embeddings only. Documents already stored in a vector store keep the old dimension, so retrieval fails with dimension-mismatch errors until those documents are re-ingested. Keep one embedding model per corpus, or clear and re-ingest after a switch.

## Model catalog

The catalog is fetched lazily from `https://models.dev/api.json` (filters by enabled flag, cached with configurable TTL). It is **advisory**: switching to a model not in the catalog logs a warning but is always allowed — so local-only Ollama models keep working even when the catalog is unreachable.

## Architecture

```
domain/                    interfaces + records (ProviderProfile, ModelSpec, ModelCatalogPort)
  ├── ProviderType         OLLAMA | OPENAI | OPENAI_COMPATIBLE
  ├── ProviderProfileStore persists provider profiles (API keys included, best-effort)
  └── ModelCatalogPort     catalog source abstraction
services/
  ├── ProviderRegistry     seeded from env + restored from file; runtime upsert via /api/provider/configure
  ├── ModelRouter          active chat/embedding specs + live clients, lazy dimension detection
  └── ModelCatalogService  caching facade over ModelCatalogPort
adapter/
  ├── ProviderClientFactory builds Spring AI clients per ProviderType (Ollama/OpenAI APIs)
  ├── ProviderProfileFileStore JSON file persistence
  └── ModelsDevCatalogClient models.dev api.json fetcher (defensive parsing)
api/                       ProviderController, ComputeController, CatalogController, ApiExceptionHandler
config/                    RagProviderConfig wires everything; Spring AI auto-config excluded (clients built explicitly)
```

See also:
- [ADR-0010: Dedicated rag-provider Service for Model Switching](../../docs/architecture/decision-records/adr-0010-rag-provider.md)
- [ADR-0011: Refreshable Model Catalog (Models.dev)](../../docs/architecture/decision-records/adr-0011-model-catalog.md)
- [ADR-0012: TUI connect Command](../../docs/architecture/decision-records/adr-0012-tui-connect.md)