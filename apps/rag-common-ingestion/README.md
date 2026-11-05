# rag-common-ingestion

Shared **ingestion pipeline** building blocks: document parsing, text chunking, and the ingestion services that tie them together. A **library** — no runnable app, no HTTP port. Consumed by rag-basic, rag-advanced, and rag-tui.

## Purpose

Implement the parse → split → embed → store flow **once** so every `rag-*` module shares the same ingestion behavior while keeping its own chunking strategy. The pipeline is:

```
Document (content or raw bytes)
  → DocumentParser (Tika / plain text)     → plain text
  → TextSplitter (fixed / recursive / token / semantic) → List<Chunk>
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
    ├── TokenChunker            - splits to match embedding model token windows
    └── SemanticChunker         - cuts where sentence embeddings show a topic shift
```

## Chunking strategies

| Strategy | When to use |
|----------|-------------|
| Fixed-size sliding window | Homogeneous text, baseline comparisons |
| Recursive character | Most cases — the default (`rag.chunking.strategy=recursive`) |
| Token-based | Match the embedding model's token window |
| Semantic | High-value documents where topic shifts matter; needs an embedding model at split time |

All chunkers implement the `TextSplitter` port from rag-common-core, so modules pick one via config (`RagBasicConfig`/`RagAdvancedConfig`) with no code change. Defaults: `CHUNK_SIZE=512`, `CHUNK_OVERLAP=128`.

## Semantic chunking cost (measured)

`ChunkingCostComparisonTest` measures how many texts each chunker sends to the embedding port over this repository's own `docs/` corpus (33 markdown files, ~150 KB, window 512/64), with a local hashing model standing in for a provider. Measured on `main` (`71773ee`); the figures are approximate because the corpus is `docs/` itself:

| Strategy | Chunks | Chunks/doc | Texts embedded | vs recursive |
|----------|--------|------------|----------------|--------------|
| Fixed-size | ≈350 | ≈10.5 | ≈350 | 0.8x |
| Recursive | ≈410 | ≈12.5 | ≈410 | 1.0x |
| Token | ≈190 | ≈5.8 | ≈190 | 0.5x |
| Semantic | ≈630 | ≈19.2 | ≈1,850 (≈1,220 sentences + ≈630 chunks) | ≈4.5x |

`IngestionService` embeds every chunk once, so a strategy's ingestion cost is its chunk count — except `SEMANTIC`, which also embeds every sentence of a document to find its topic shifts (`sentences + chunks`). Semantic additionally yields more, smaller chunks than recursive (19.2 vs 12.5 per document), so the measured premium is **~4.5x**, not the 8x older docs quoted. The ratio is the same at the default 512/128 window, because overlap changes chunk text, not sentence grouping.

Reproduce with `mvn -pl apps/rag-common-ingestion -am test -Dtest=ChunkingCostComparisonTest`.

### Reading those numbers correctly

- **The counts move with `docs/`.** The corpus is the documentation tree itself, so any edit under `docs/` changes the chunk counts — a docs table measured against an earlier tree will not reproduce (this README is not in the corpus, so editing it does not). The test asserts only that semantic costs more than 2x recursive, not the exact figures above: re-run it and update the table after doc changes instead of trusting it.
- **They are texts, not HTTP requests.** `RemoteEmbeddingModel` folds a whole list into one `POST /api/embed`, and `IngestionService` sends at most `rag.embedding.batch-size` (default 100) chunks per request. Semantic therefore pays one extra request per document — for its sentences — not one per sentence; and that sentence batch is *not* capped by the batch-size setting, unlike chunk embedding.
- **`size` bounds the sentence grouping, not the final chunk.** With `overlap > 0` the previous chunk's tail is prepended after grouping, so a chunk can exceed `size` by up to `overlap` characters (612 measured with the default 512/128 window).
- **Split times are not portable.** They are local CPU/JIT measurements (single-digit to ~40 ms for the whole corpus) with no provider latency, so they support one conclusion: provider calls dominate ingestion cost, not chunking.
- **Retrieval precision is not measured here.** The ~71/82/91% figures in the docs are literature references; `rag-evaluation` has no precision harness yet.

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