# rag-common-generation

Shared **LLM and embedding compute** adapters, plus the chat orchestration service. A **library** — no runnable app, no HTTP port. Consumed by rag-basic, rag-advanced, and rag-tui.

## Purpose

Bridge the `ChatModelPort` / `EmbeddingModelPort` strategy interfaces to either **local Spring AI clients** (Ollama/OpenAI) or the **centralized rag-provider** over HTTP — and provide the retrieve → generate flow used to answer questions.

## Contents

```
com.rag.common.generation
├── ChatService              - retrieve (VectorStorePort) → prompt → generate (ChatModelPort)
├── StreamingChatModelPort   - streaming chat abstraction
└── adapter/
    ├── SpringAiChatModel        - local Spring AI ChatModel client (Ollama/OpenAI)
    ├── SpringAiEmbeddingModel   - local Spring AI EmbeddingModel client
    ├── RemoteChatModelPort      - ChatModelPort that calls rag-provider over HTTP
    ├── RemoteEmbeddingModel     - EmbeddingModelPort that calls rag-provider over HTTP
    └── ProviderHttpClient       - minimal JDK HttpClient for rag-provider (JSON + SSE, W3C traceparent header)
```

## Two wiring modes

| Mode | Adapter | How it's configured |
|------|---------|---------------------|
| **Provider hub (default)** | `RemoteChatModelPort`, `RemoteEmbeddingModel` | `rag.provider.url` (env `RAG_PROVIDER_URL`, default `http://localhost:8086`). Modules talk to rag-provider; they add **no** provider SDK dependencies. |
| **Direct Spring AI** | `SpringAiChatModel`, `SpringAiEmbeddingModel` | Spring AI auto-configuration for Ollama/OpenAI (used by rag-tui's `local`/`cloud` profiles). |

`ProviderHttpClient` is a dependency-free JSON/SSE client (JDK `HttpClient`), so `rag-common-generation` needs no Spring Web dependency of its own. When a `Tracer` is present it forwards the current span as a W3C `traceparent` header so rag-provider keeps the same `traceId`.

## Why providers go through rag-provider

Centralizing every provider client in [rag-provider](../rag-provider/README.md) gives: a single place for API keys/models, **runtime model switching** via an HTTP call (no restart), lazy embedding-dimension detection, and persisted provider profiles. See [ADR-0010](../../docs/architecture/decision-records/adr-0010-rag-provider.md).

## Configuration

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `rag.provider.url` | `RAG_PROVIDER_URL` | `http://localhost:8086` | Provider hub base URL |
| `rag.embedding.batch-size` | `RAG_EMBED_BATCH_SIZE` | `100` | Embedding batch size |

## Testing

```bash
.\scripts\test.bat rag-common-generation
.\scripts\test.bat rag-common-generation --coverage
```

## Related

- [rag-provider](../rag-provider/README.md) — the HTTP hub these adapters talk to
- [rag-common-core](../rag-common-core/README.md) — `ChatModelPort`/`EmbeddingModelPort` ports
- [ADR-0010: rag-provider service](../../docs/architecture/decision-records/adr-0010-rag-provider.md)