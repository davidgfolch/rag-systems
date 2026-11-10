# rag-advanced

Production-grade RAG with candidate widening, BM25-style lexical re-ranking fused with the vector order (RRF), exact metadata filtering, and pipeline variants. A runnable Spring Boot app on port **8082** (env `RAG_ADVANCED_URL`). It is the direct "quality upgrade" over [rag-basic](../rag-basic/README.md).

## Purpose

Extend rag-basic with techniques that materially improve retrieval quality — candidate widening, a BM25-style lexical re-rank fused with the vector order via Reciprocal Rank Fusion (RRF), and exact metadata filtering — so you can measure *how much* better each technique is against the baseline. How it works end to end: [rag-advanced deep dive](../../docs/architecture/modules/rag-advanced.md).

## Layers

```
com.rag.advanced
├── api/             - QueryController (/api/query, /api/query/advanced), AdvancedQueryRequest
├── config/          - RagAdvancedConfig (shared pipeline + retrieval beans)
├── services/        - AdvancedRetrievalService
└── retrieval/       - LexicalScorer (BM25-style), MetadataFilter
```

The shared `IngestionController`, `ChatService`, `ChatWebSocketHandler`, parser and variant wiring come from `rag-common-*`; this module only adds the retrieval stage.

## Advanced Features

| Feature | Description | Why it helps |
|---------|-------------|--------------|
| Candidate widening | Fetch `topK × 4` vector hits before re-ranking | Gives the lexical pass candidates it can promote |
| Lexical re-ranking | BM25-style score (k1=1.2, b=0.75, δ=0.5) over the candidate set | Rewards exact/rare terms that embeddings blur |
| Hybrid fusion | RRF over vector rank + lexical rank: `1/(60+r+1)` each | Combines both signals without score normalization |
| Metadata filtering | Exact key/value match on chunk metadata before fusion | Removes off-topic sources early |
| Pipeline variants | Boot + runtime variants with their own table | Compare chunking/model choices per module |

**Not implemented:** cross-encoder/LLM re-ranking, query transformation/expansion, and contextual compression. The implemented re-ranker is the lexical BM25 + RRF pass above.

## Pipeline variants

A **variant** bundles a chunking strategy + window with the embedding model label and its own vector table. rag-advanced seeds a `boot` variant from `rag.chunking.*` that keeps the module's `chunks` table; variants registered at runtime own `variant_<id>` inside `rag_advanced`, so a chunking experiment never mixes with the baseline vectors.

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/modules/rag-advanced/variants` | List variants |
| `GET` | `/api/modules/rag-advanced/variants/active` | Active variant (`204` when none) |
| `POST` | `/api/modules/rag-advanced/variants` | Register a variant (strategy + window) |
| `POST` | `/api/modules/rag-advanced/variants/{id}/activate` | Switch without restarting the module |
| `PUT` / `DELETE` | `/api/modules/rag-advanced/variants/{id}` | Update / remove |

A switch applies to the **next** ingestion and query. Each variant embeds with its own `embeddingModel` via rag-provider, so variants can use different dimensions side by side; `connect embedding` remains the fallback for the `boot` variant. Registry and active variant persist in `pipeline_variants`. See [ADR-0015](../../docs/architecture/decision-records/adr-0015-pipeline-variants.md).

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

> `/api/query` and `/api/query/advanced` use the advanced pipeline (widening + metadata filter + BM25/RRF fusion). `/ws/chat` uses the shared `ChatService`, which retrieves with the vector store directly, so chat answers use plain vector top-K.

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
| `spring.ai.vectorstore.pgvector.schema-name` | `PGVECTOR_ADVANCED_SCHEMA` | `rag_advanced` | PgVector schema (isolated from rag-basic) |

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

Why bother: each feature above (candidate widening, lexical/BM25 fusion, metadata filter) is a deliberate trade of extra compute for better retrieval. This workflow shows whether that trade pays off *for your corpus*, and [performance-metrics.md](../../docs/comparison/performance-metrics.md) records the published numbers.

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