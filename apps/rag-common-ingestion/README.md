# rag-common-ingestion

Shared **ingestion pipeline** building blocks: document parsing, text chunking, and the ingestion services that tie them together. A **library** — no runnable app, no HTTP port. Consumed by rag-basic, rag-advanced, and rag-tui.

## Purpose

Implement the parse → split → embed → store flow **once** so every `rag-*` module shares the same ingestion behavior while keeping its own chunking strategy. The pipeline is:

```
Document (content or raw bytes)
  → DocumentParser (Tika / plain text)     → plain text
  → TextSplitter (fixed / recursive / token) → List<Chunk>
  → EmbeddingModelPort (via rag-provider)  → chunk embeddings
  → VectorStorePort (PgVector / in-memory) → stored
```

## Contents

```
com.rag.common.ingestion
├── IngestionService        - synchronous parse → split → embed → store; tracks chunk counts, skips empty extractions
├── AsyncIngestionService   - same pipeline on a scheduler; exposes job status (SUBMITTED/PROCESSING/COMPLETED/FAILED)
├── parsing/
│   ├── TikaDocumentParser  - Apache Tika: PDF, DOCX, HTML, TXT, ... (reads raw bytes from metadata)
│   └── PlainTextParser     - trivial passthrough for already-plain content
└── chunking/
    ├── FixedSizeChunker        - fixed-size sliding window
    ├── RecursiveCharacterChunker - recursive splitting on separators (default)
    └── TokenChunker            - splits to match embedding model token windows
```

## Chunking strategies

| Strategy | When to use |
|----------|-------------|
| Fixed-size sliding window | Homogeneous text, baseline comparisons |
| Recursive character | Most cases — the default (`rag.chunking.strategy=recursive`) |
| Token-based | Match the embedding model's token window |

All chunkers implement the `TextSplitter` port from rag-common-core, so modules pick one via config (`RagBasicConfig`/`RagAdvancedConfig`) with no code change. Defaults: `CHUNK_SIZE=512`, `CHUNK_OVERLAP=128`.

## Async ingestion

`AsyncIngestionService` submits a document and returns a `documentId` immediately (HTTP 202). Poll the job state — `PENDING`, `RUNNING`, `COMPLETED` (with chunk count), `FAILED` — while embedding runs in the background. Used by `POST /api/documents/ingest-file-async` (see the runnable module READMEs).

## Why a separate module?

Keeping parsing + chunking here means: one Tika/parser dependency, shared de-duplication (SHA-256 content hash via `MetadataKeys.CONTENT_HASH`), and one place to tune chunking defaults. Modules stay small and their `ArchitectureTest` can enforce "no direct parser/chunker code in the app module".

## Testing

```bash
.\scripts\test.bat rag-common-ingestion
.\scripts\test.bat rag-common-ingestion --coverage
```

## Related

- [Chunking strategies guide](../../docs/guides/chunking-strategies.md)
- [rag-common-core](../rag-common-core/README.md) — `TextSplitter`/`DocumentParser`/`EmbeddingModelPort` ports
- [rag-common-retrieval](../rag-common-retrieval/README.md) — where chunks are stored
- [rag-provider](../rag-provider/README.md) — supplies the embeddings