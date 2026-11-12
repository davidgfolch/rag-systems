# rag-common-app

Shared **pipeline wiring** for the `rag-*` runtimes. A **library** — no runnable app, no HTTP port. Consumed by rag-basic, rag-advanced, and rag-agentic.

## Purpose

Hosts `RagPipelineConfiguration`, the single `@Configuration` that assembles the cross-cutting beans every runnable RAG module needs: chunking, parsing, embedding, vector storage, ingestion, chat, and the WebSocket trace interceptor. Each module `@Import`s it and adds only its own strategy beans, so no chunk of the pipeline configuration is duplicated across modules.

## Why a separate module

The pipeline wires beans from `rag-common-core`, `rag-common-ingestion`, `rag-common-retrieval`, and `rag-common-generation` together. None of those modules may reference each other (ArchUnit-enforced per modularization ADR-0013), so the composition point lives in its own `rag-common-app` module with all four as dependencies.

## Contents

```
com.rag.common.app
├── config/
│   └── RagPipelineConfiguration   - text splitter, parser, embedding, vector store,
│                                    ingestion services, chat model/service, trace interceptor
└── pipeline/
    ├── VariantStore               - per-module variant registry + active variant
    ├── VariantStoreFactory        - builds the variant_<id> pgvector store
    ├── ActiveTextSplitter         - splitter delegate that follows the active variant
    ├── ActiveVectorStore          - store delegate that follows the active variant
    ├── PipelineVariantController  - /api/modules/{module}/variants endpoints
    ├── Chunkers                   - strategy -> chunker factory
    └── PgVectorSchemas            - schema override / dimension fallback
```

Beans provided (all overridable per module):

- **Ingestion**: `textSplitter` (variant-aware delegate), `documentParser`, `ingestionService`, `asyncIngestionService`
- **Embedding/vector**: `remoteEmbeddingModel`, `embeddingModel`, `pgVectorStore` (lazy), `bootVectorStore` (lazy), `vectorStore` (`@Primary` variant-aware delegate)
- **Variants**: `variantStore`, `variantStoreFactory`, `pipelineVariantController`
- **Generation**: `chatModel`, `streamingChatModel`, `chatService`, `remoteChatModelPort`
- **WebSocket**: `traceHandshakeInterceptor`

## Pipeline variants

Each importing module gets a variant registry and the `/api/modules/{module}/variants` endpoints. The module seeds a `boot` variant from `rag.chunking.*` (it keeps the module's existing `chunks` table); runtime variants own `variant_<id>` in the module schema. `ActiveTextSplitter` and `ActiveVectorStore` resolve the active variant per call, so activation applies to the next ingestion/query without a restart.

The registry and active id are persisted in `pipeline_variants` inside the module schema, so variants survive a restart; deleting a variant drops its `variant_<id>` table. Ingestion and retrieval resolve the active variant's `embeddingModel` through `ActiveEmbeddingModelPort`, and rag-provider builds that model per request, so variants can embed differently at the same time. See [ADR-0015](../../docs/architecture/decision-records/adr-0015-pipeline-variants.md).

## Configuration

| Property                                   | Env var            | Default                 | Description                                  |
|--------------------------------------------|--------------------|-------------------------|----------------------------------------------|
| `rag.provider.url`                         | `RAG_PROVIDER_URL` | `http://localhost:8086` | Provider hub base URL                        |
| `rag.provider.embedding.default-dimension` | -                  | `768`                   | Dimension label of the seeded `boot` variant |

## Testing

```bash
.\scripts\test.bat rag-common-app
.\scripts\test.bat rag-common-app --coverage
```

## Related

- [rag-common-ingestion](../rag-common-ingestion/README.md), [rag-common-retrieval](../rag-common-retrieval/README.md), [rag-common-generation](../rag-common-generation/README.md) — the capability modules it composes
- [ADR-0013: Split rag-common into capability modules](../../docs/architecture/decision-records/adr-0013-rag-common-split.md)