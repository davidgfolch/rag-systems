# rag-common-core

The **kernel** of the monorepo: domain models, strategy ports, tracing helpers, and shared utilities. Every other module depends on this one. It is a **library** — no runnable app, no HTTP port.

## Purpose

Define the vocabulary and the seams of the whole system so the `rag-*` implementations stay decoupled and interchangeable:

- **Domain models** — what a `Document`, `Chunk`, and `DocumentSummary` are, once.
- **Strategy ports** — the interfaces (chunking, parsing, embedding, chat, vector store) that each implementation plugs into.
- **Tracing** — `traceId`/`spanId` propagation across processes and threads.
- **Utilities** — content hashing, metadata keys, float conversions.

## Contents

```
com.rag.common.core
├── domain/          - Document, Chunk, DocumentSummary, MetadataKeys, FloatConversions
├── services/        - ChatModelPort, EmbeddingModelPort, DocumentParser, TextSplitter, FileDocumentLoader
├── repositories/    - VectorStorePort
├── tracing/         - TracePropagation (W3C traceparent propagation)
└── util/            - Hashes (SHA-256 helpers)
```

### Domain models

| Type | Role |
|------|------|
| `Document` | Raw ingested source with `id`, `content` (or raw bytes in metadata), `metadata`, `createdAt` |
| `Chunk` | A segmented piece of a document ready for embedding: `id`, `documentId`, `content`, `index`, `metadata`, mutable `embedding` |
| `DocumentSummary` | Lightweight view (`documentId`, `metadata`, `chunkCount`) for listing documents |
| `MetadataKeys` | Shared constants for the chunk/document metadata keys (`SOURCE`, `SOURCE_TYPE`, `TITLE`, `FILE_NAME`, `CONTENT_HASH`, `RAW`, ...) |
| `FloatConversions` | Convert between `float[]` and `List<Float>` embeddings |

### Strategy ports

| Port | Contract target | Implementations |
|------|-----------------|-----------------|
| `ChatModelPort` | Chat completion (generate/summarize) | rag-common-generation |
| `EmbeddingModelPort` | Text → vector embeddings | rag-common-generation |
| `DocumentParser` | Raw bytes/content → text | rag-common-ingestion |
| `TextSplitter` | Text → chunks | rag-common-ingestion |
| `VectorStorePort` | Store/query chunk vectors | rag-common-retrieval |
| `FileDocumentLoader` | Read local files/folders into `Document` objects | in-module |

Because the ports live here, a module can import the abstraction and let `config/` wire a concrete implementation — no hard dependencies on any provider or store.

## Why a separate module?

It enforces **DRY** (no duplicated domain/ports across modules) and **dependency direction** (everything else depends on the kernel; the kernel depends on nothing). The layer rules, naming conventions (`Document`/`Chunk` plain, DTOs suffixed), and magic-literal policy for these types are enforced by `ArchitectureTest`.

## Testing

```bash
.\scripts\test.bat rag-common-core
.\scripts\test.bat rag-common-core --coverage
```

## Related

- [rag-contract](../rag-contract/README.md) — the OpenAPI DTOs that cross the wire (distinct from these domain beans)
- [ADR-0001: Monorepo layout](../../docs/architecture/decision-records/adr-0001-monorepo-layout.md)
- [Architecture overview](../../docs/architecture/overview.md)