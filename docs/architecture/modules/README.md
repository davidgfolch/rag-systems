# RAG Module Deep Dives

This section explains **what each retrieval variant does and how it works**, as implemented in the code. It complements the [module READMEs](../../../README.md#modules--strategy), which cover setup, ports and operational commands, and the [architecture overview](../overview.md), which covers the shared layering.

| Module           | Port | What makes it different                                                                                                    | Deep dive                       |
|------------------|------|----------------------------------------------------------------------------------------------------------------------------|---------------------------------|
| **rag-basic**    | 8081 | Baseline: one vector similarity search, no re-ranking or filters                                                           | [rag-basic](rag-basic.md)       |
| **rag-advanced** | 8082 | Widens the vector pass, then fuses vector order with a BM25-style lexical pass (RRF) and supports exact metadata filtering | [rag-advanced](rag-advanced.md) |
| **rag-agentic**  | 8083 | A bounded agent loop plans sub-queries, chooses tools, reflects on sufficiency and then answers                            | [rag-agentic](rag-agentic.md)   |

## They share one pipeline

The three modules do **not** each re-implement ingestion, parsing, chunking or chat. All three `@Import` the same shared wiring and keep only their retrieval/agent strategy:

```
com.rag.basic / com.rag.advanced / com.rag.agentic
        │
        ├── api/        module query controller (contract + variant-specific endpoint)
        ├── config/     @Import(RagPipelineConfiguration, WebSocketConfiguration)
        └── services/   module retrieval service (+ agents/tools for rag-agentic)

rag-common-app          RagPipelineConfiguration: parser, variants, stores, ingestion, chat, WS
rag-common-ingestion    IngestionController, IngestionService, AsyncIngestionService, crawl
rag-common-generation   ChatService, ChatWebSocketHandler, provider adapters
rag-common-retrieval    PgVectorStoreAdapter, InMemoryVectorStore
rag-common-core         Chunk/Document domain + ports (VectorStorePort, ChatModelPort, ...)
```

### Shared ingestion

`IngestionController` (`/api/documents/*`) parses the source with the format-aware parser, chunks it with the **active pipeline variant**, embeds in batches, and writes to the active vector store:

```
Document → DocumentParser → ParsedDocument → ActiveDocumentChunker → EmbeddingModelPort → VectorStorePort
```

| Concern       | Implementation                                                                                                                    |
|---------------|-----------------------------------------------------------------------------------------------------------------------------------|
| Parsing       | Format-aware by default (`PDF`, `DOCX`, `HTML`, `Markdown`, `JSON`, text); `rag.parsing.mode=auto\|tika\|plain`                   |
| Chunking      | `ActiveDocumentChunker` wraps the active variant's `TextSplitter` in a structural dispatcher                                      |
| Embedding     | Batched by `rag.embedding.batch-size` (default 100 in module config) through `ActiveEmbeddingModelPort`                           |
| Stores        | PgVector schema per module; `simple` selects `InMemoryVectorStore`                                                                |
| Deduplication | `PgVectorStoreAdapter` over-fetches up to 4× for deduplication (multiplier stops above 256) and collapses identical chunk content |
| Async         | `ingest-file-async` runs on virtual threads with a job map + `rag.ingestion.max-concurrent` limiter                               |
| URL ingestion | The module calls `rag-webcrawler` through `WebCrawlerClient`; crawl sessions are orchestrated by `CrawlSessionService`            |

### Shared pipeline variants

A **variant** bundles a chunking strategy + window + embedding model with its own storage table:

- The `boot` variant is seeded from `rag.chunking.*` and keeps the module's `chunks` table.
- Runtime variants own `variant_<id>` inside the same module schema (e.g. `rag_advanced.variant_v2`).
- Activation changes the store/chunker/embedding for the **next** ingestion and query, without restarting.
- The registry and active variant persist in `pipeline_variants`, so they survive restarts.

`ActiveVectorStore`, `ActiveDocumentChunker` and `ActiveEmbeddingModelPort` resolve the active variant on every call. See [ADR-0015](../decision-records/adr-0015-pipeline-variants.md).

| Method   | Path                                                  | Purpose                          |
|----------|-------------------------------------------------------|----------------------------------|
| `GET`    | `/api/modules/{module}/variants`                      | List variants                    |
| `GET`    | `/api/modules/{module}/variants/active`               | Active variant (`204` when none) |
| `GET`    | `/api/modules/{module}/variants/{variantId}`          | Get one variant                  |
| `POST`   | `/api/modules/{module}/variants`                      | Register a variant               |
| `PUT`    | `/api/modules/{module}/variants/{variantId}`          | Update (new window/model)        |
| `POST`   | `/api/modules/{module}/variants/{variantId}/activate` | Activate                         |
| `DELETE` | `/api/modules/{module}/variants/{variantId}`          | Remove and drop its table        |

### Shared chat and streaming

`/ws/chat` accepts `{"type":"ask","conversationId":"...","question":"...","topK":4}` and `{"type":"cancel","conversationId":"..."}`. The handler emits `token` frames, then one `done` frame carrying the full answer, or an `error` frame. Cancelling or disconnecting disposes the underlying `Flux`, stopping the provider stream.

> **Important nuance:** `ChatWebSocketHandler` is wired to the module's answer stream. `rag-basic` and `rag-advanced` use the shared `ChatService`, which always retrieves with the vector store directly. Only `rag-agentic` overrides the handler with `AgenticChatService`, so its chat runs the agent loop. In `rag-advanced`, the BM25+RRF logic applies to `/api/query*`, **not** to `/ws/chat`.

### Shared observability and data isolation

- Actuator: `/actuator/health`, `/actuator/prometheus`, `/actuator/metrics`.
- Each module owns a PostgreSQL schema (`rag_basic`, `rag_advanced`, `rag_agentic`) — never shared, so a comparison never mixes vectors ([ADR-0006](../decision-records/adr-0006-data-store-isolation.md)).
- Chat and embedding calls go through `rag-provider` (`rag.provider.url`, default `http://localhost:8086`); no module holds a provider client directly ([ADR-0010](../decision-records/adr-0010-rag-provider.md)).

## Choosing a variant

| Question                                                       | Start with                                                  |
|----------------------------------------------------------------|-------------------------------------------------------------|
| Do you need the simplest possible RAG, fast and cheap?         | rag-basic                                                   |
| Do keyword-heavy queries or exact terms matter?                | rag-advanced                                                |
| Do you need multi-hop questions or data spread across sources? | rag-agentic                                                 |
| Are you benchmarking retrieval quality per technique?          | Run all three on the same corpus and compare (`/api/query`) |

See also [Trade-offs](../../comparison/trade-offs.md), [Performance Metrics](../../comparison/performance-metrics.md) and [Getting Started](../../guides/getting-started.md).
