# rag-basic

The **baseline** RAG implementation: document ingestion with fixed/recursive chunking and basic vector similarity search. A runnable Spring Boot app on port **8081** (env `RAG_BASIC_URL`). This is the educational reference implementation and the baseline against which rag-advanced and rag-agentic are compared.

## Purpose

Demonstrate the core RAG pipeline — **Ingest → Chunk → Embed → Store → Retrieve → Generate** — with simple, understandable components. Use as the baseline for quantitative and qualitative comparison with the other modules.

## Layers

```
com.rag.basic
├── api/             - IngestionController, QueryController, chat WebSocketHandler
├── config/          - RagBasicConfig, WebSocketConfig, TraceHandshakeInterceptor
└── services/        - RetrievalService, WebCrawlerClient
```

The heavy lifting (parsing, chunking, stores, adapters) comes from the `rag-common-*` libraries so this module stays thin.

## Chunking Strategies

| Strategy | When to use |
|----------|-------------|
| Fixed-size sliding window | Homogeneous text, baseline |
| Recursive character | Most cases, default |
| Token-based | Match embedding model windows |

## How to run

**rag-provider must be running first** (it supplies chat + embeddings). Then start rag-basic:

```bash
# 0. Provider hub (once)
.\scripts\run.bat rag-provider --profile local

# 1. rag-basic (own terminal; Windows)
.\scripts\run.bat rag-basic --profile local

# Linux/Mac
./scripts/run.sh rag-provider --profile local
./scripts/run.sh rag-basic --profile local
```

Cloud profile (OpenAI, needs `OPENAI_API_KEY` in `.env.secrets`):

```bash
.\scripts\run.bat rag-basic --profile cloud
```

Data is stored in the PostgreSQL schema `rag_basic` (start it with `.\scripts\docker.bat up`). Switch the whole app to the in-memory store with `VECTOR_STORE_TYPE=simple`.

## Quick access

| Endpoint | URL |
|----------|-----|
| API base | <http://localhost:8081> |
| Health | <http://localhost:8081/actuator/health> |
| Prometheus metrics | <http://localhost:8081/actuator/prometheus> |
| API reference | [rag-api.yaml](../rag-contract/src/main/resources/openapi/rag-api.yaml) |

There is no per-module Swagger UI — the contract is defined once in [rag-contract](../rag-contract/README.md).

## HTTP API

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/documents/ingest-file` | Multipart ingest (`file` part) |
| `POST` | `/api/documents/ingest-file-async` | Async ingest, returns a job id (poll `/api/documents/ingest-status/{id}`) |
| `POST` | `/api/documents/ingest` | Ingest raw content `{"content": "...", "metadata": {...}}` |
| `POST` | `/api/documents/ingest-url` | Ingest a web page via rag-webcrawler |
| `GET` | `/api/documents` | List ingested documents |
| `DELETE` | `/api/documents/{documentId}` | Remove a document |
| `POST` | `/api/query` | `{"question":"...","topK":4,"documentId":"optional"}` → retrieved chunks |
| `WS` | `/ws/chat` | Streaming chat (WebSocket frames) |

Examples:

```bash
# Ingest a file
curl -F "file=@docs/report.pdf" http://localhost:8081/api/documents/ingest-file

# Ingest a web page
curl -X POST http://localhost:8081/api/documents/ingest-url \
  -H "Content-Type: application/json" -d '{"url":"https://spring.io/projects/spring-ai"}'

# Ask a grounded question
curl -H "Content-Type: application/json" \
  -d '{"question":"What is this document about?","topK":4}' \
  http://localhost:8081/api/query
```

## Configuration

Runtime knobs (all settable via env vars in `.env` / `.env.secrets`):

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `server.port` | `RAG_BASIC_PORT` | `8081` | HTTP port (must match the port in `RAG_BASIC_URL`) |
| `rag.provider.url` | `RAG_PROVIDER_URL` | `http://localhost:8086` | Provider hub |
| `rag.vector-store.type` | `VECTOR_STORE_TYPE` | `pgvector` | `pgvector` or `simple` |
| `rag.chunking.strategy` | - | `recursive` | `fixed`/`recursive`/`token` |
| `rag.chunking.size` | `CHUNK_SIZE` | `512` | Max chunk size (chars) |
| `rag.chunking.overlap` | `CHUNK_OVERLAP` | `128` | Chunk overlap |
| `rag.embedding.batch-size` | `RAG_EMBED_BATCH_SIZE` | `100` | Embedding batch size |
| `spring.ai.vectorstore.pgvector.schema-name` | `PGVECTOR_SCHEMA` | `rag_basic` | PgVector schema |
| `SPRING_PROFILES_ACTIVE` | - | `local` | `local` (Ollama) or `cloud` (OpenAI) |

## Performance Notes

- Embeddings run fast on CPU (nomic-embed-text, ~580 chunks/sec)
- Generation is the only GPU-hungry layer; use small models on CPU
- See [performance metrics](../../docs/comparison/performance-metrics.md)

## Testing

```bash
.\scripts\test.bat rag-basic
.\scripts\test.bat rag-basic --coverage
.\scripts\test.bat
```

## Related

- [rag-advanced](../rag-advanced/README.md) — the retrieval-quality upgrade over this baseline
- [rag-evaluation](../rag-evaluation/README.md) — the benchmark harness to compare them
- [Get started guide](../../docs/guides/getting-started.md)