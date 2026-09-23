# rag-advanced

Production-grade RAG with reranking, hybrid search, semantic chunking, and metadata filtering. A runnable Spring Boot app on port **8082** (env `RAG_ADVANCED_URL`). It is the direct "quality upgrade" over [rag-basic](../rag-basic/README.md).

## Purpose

Extend rag-basic with techniques that materially improve retrieval quality — two-stage retrieval (fast search + cross-encoder/LLM rerank), hybrid vector+keyword search, and metadata-aware filtering — so you can measure *how much* better each technique is against the baseline.

## Layers

```
com.rag.advanced
├── api/             - IngestionController, QueryController, chat WebSocketHandler
├── config/          - RagAdvancedConfig, WebSocketConfig, TraceHandshakeInterceptor
├── services/        - AdvancedRetrievalService, WebCrawlerClient
├── retrieval/       - LexicalScorer, MetadataFilter
└── (reranking, hybrid, query transformation live in AdvancedRetrievalService)
```

## Advanced Features

| Feature | Description | Why it helps |
|---------|-------------|--------------|
| Reranking | Re-score top-K candidates with a cross-encoder/LLM | Precision: drops borderline matches that plain similarity keeps |
| Hybrid search | Combine semantic + lexical (BM25) scores | Robustness: catches exact keyword matches vectors miss |
| Metadata filtering | Filter by source, doc_type, language, date | Focuses retrieval before reranking, cutting noise |
| Query transformation | Expand/rewrite queries | Handles vague or multi-part questions |
| Contextual compression | Reduce noise in retrieved context | Fits more signal into the LLM prompt |

## How to run

**rag-provider must be running first.** rag-advanced also needs PostgreSQL for its own schema `rag_advanced`.

```bash
# 0. Provider hub (once)
.\scripts\run.bat rag-provider --profile local

# 1. rag-advanced (Windows)
.\scripts\run.bat rag-advanced --profile local

# Linux/Mac
./scripts/run.sh rag-provider --profile local
./scripts/run.sh rag-advanced --profile local
```

## Quick access

| Endpoint | URL |
|----------|-----|
| API base | <http://localhost:8082> |
| Health | <http://localhost:8082/actuator/health> |
| Prometheus metrics | <http://localhost:8082/actuator/prometheus> |
| API reference | [rag-api.yaml](../rag-contract/src/main/resources/openapi/rag-api.yaml) |

## HTTP API

Same contract as rag-basic (shared [rag-contract](../rag-contract/README.md)) — `/api/documents/*` and `/api/query` — plus the streaming `/ws/chat` WebSocket. The difference is *what happens inside* `AdvancedRetrievalService` when a query arrives.

```bash
curl -F "file=@docs/report.pdf" http://localhost:8082/api/documents/ingest-file

curl -H "Content-Type: application/json" \
  -d '{"question":"What is this document about?","topK":4}' \
  http://localhost:8082/api/query
```

## Configuration

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `server.port` | `RAG_ADVANCED_PORT` | `8082` | HTTP port (must match the port in `RAG_ADVANCED_URL`) |
| `rag.provider.url` | `RAG_PROVIDER_URL` | `http://localhost:8086` | Provider hub |
| `rag.vector-store.type` | `VECTOR_STORE_TYPE` | `pgvector` | `pgvector` or `simple` |
| `rag.chunking.strategy` | - | `recursive` | `fixed`/`recursive`/`token` |
| `rag.chunking.size` | `CHUNK_SIZE` | `512` | Max chunk size |
| `rag.chunking.overlap` | `CHUNK_OVERLAP` | `128` | Chunk overlap |
| `spring.ai.vectorstore.pgvector.schema-name` | `PGVECTOR_SCHEMA` | `rag_advanced` | PgVector schema (isolated from rag-basic) |

## How to compare it with rag-basic (the "why")

Both modules expose the identical API and answer the same questions, which makes a fair A/B comparison possible. The comparison workflow:

1. **Start the observability stack** so metrics/traces flow: `.\scripts\docker.bat up-obs`.
2. **Run both modules with the same profile + observability** — `.\scripts\run.bat rag-basic --profile local,observability` and `.\scripts\run.bat rag-advanced --profile local,observability` (in separate terminals; rag-provider must be up).
3. **Ingest the identical corpus** into each (`/api/documents/ingest-file`) — data stays isolated per schema (`rag_basic` vs `rag_advanced`).
4. **Drive identical questions** to each `/api/query` endpoint, same `question`/`topK`.
5. **Compare on three axes**:
   - **Retrieval quality** — precision/recall/relevance of returned chunks (from [rag-evaluation](../rag-evaluation/README.md) or eyeball returned `results`).
   - **Latency & cost** — Grafana "RAG Pipeline Overview" + "Cost & Usage" dashboards (see [rag-observability](../rag-observability/README.md)).
   - **End-to-end answer quality** — inspect citations in `/ws/chat` answers and the `rag_quality_*` metrics.

Why bother: each feature above (rerank, hybrid, metadata filter) is a deliberate trade of extra compute for better retrieval. This workflow shows whether that trade pays off *for your corpus*, and [performance-metrics.md](../../docs/comparison/performance-metrics.md) records the published numbers.

## Testing

```bash
.\scripts\test.bat rag-advanced
.\scripts\test.bat
```

## Related

- [rag-basic](../rag-basic/README.md) — the baseline
- [rag-evaluation](../rag-evaluation/README.md) — the benchmark harness
- [rag-observability](../rag-observability/README.md) — how to compare metrics
- [trade-offs](../../docs/comparison/trade-offs.md)