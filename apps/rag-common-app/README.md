# rag-common-app

Shared **pipeline wiring** for the `rag-*` runtimes. A **library** — no runnable app, no HTTP port. Consumed by rag-basic, rag-advanced, and rag-agentic.

## Purpose

Hosts `RagPipelineConfiguration`, the single `@Configuration` that assembles the cross-cutting beans every runnable RAG module needs: chunking, parsing, embedding, vector storage, ingestion, chat, and the WebSocket trace interceptor. Each module `@Import`s it and adds only its own strategy beans, so no chunk of the pipeline configuration is duplicated across modules.

## Why a separate module

The pipeline wires beans from `rag-common-core`, `rag-common-ingestion`, `rag-common-retrieval`, and `rag-common-generation` together. None of those modules may reference each other (ArchUnit-enforced per modularization ADR-0013), so the composition point lives in its own `rag-common-app` module with all four as dependencies.

## Contents

```
com.rag.common.app
└── config/
    └── RagPipelineConfiguration   - text splitter, parser, embedding, vector store,
                                     ingestion services, chat model/service, trace interceptor
```

Beans provided (all overridable per module):

- **Ingestion**: `textSplitter`, `documentParser`, `ingestionService`, `asyncIngestionService`
- **Embedding/vector**: `remoteEmbeddingModel`, `embeddingModel`, `pgVectorStore` (lazy), `vectorStore` (lazy)
- **Generation**: `chatModel`, `streamingChatModel`, `chatService`, `remoteChatModelPort`
- **WebSocket**: `traceHandshakeInterceptor`

## Configuration

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `rag.provider.url` | `RAG_PROVIDER_URL` | `http://localhost:8086` | Provider hub base URL |

## Testing

```bash
.\scripts\test.bat rag-common-app
.\scripts\test.bat rag-common-app --coverage
```

## Related

- [rag-common-ingestion](../rag-common-ingestion/README.md), [rag-common-retrieval](../rag-common-retrieval/README.md), [rag-common-generation](../rag-common-generation/README.md) — the capability modules it composes
- [ADR-0013: Split rag-common into capability modules](../../docs/architecture/decision-records/adr-0013-rag-common-split.md)