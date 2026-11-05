# ADR-0015: Per-module pipeline variants with per-variant pgvector tables

- **Status**: Accepted
- **Date**: 2026-11-05

## Context

Comparing RAG approaches inside one module (recursive vs fixed vs token chunking) previously required restarting the module with different `rag.chunking.*` values. Worse, every chunk landed in the same `chunks` table, so vectors produced with a different chunking window or embedding dimension were merged and could not be told apart afterwards. The TUI needs a way to switch and compare variants on one corpus without a restart or re-ingesting into the same storage.

## Decision

- A **pipeline variant** is `{variantId, module, strategy, embeddingModel, embeddingDimension, chunkSize, chunkOverlap}` (`PipelineVariantDTO`).
- Each module process keeps its own registry (`VariantStore`) and serves exactly one active variant at a time.
- At startup the module seeds a **`boot` variant** from `rag.chunking.*`. It keeps using the module's existing store (the `chunks` table in the module schema), so current data and ADR-0006 isolation are preserved.
- A variant registered at runtime owns `variant_<variantId>` inside the module schema. `VariantStoreFactory` builds one lazy `PgVectorStore` per variant; activating it writes and reads only that table. Deleting the variant drops that table again.
- The registry and the active id are persisted in a `pipeline_variants` table inside the module schema (created on demand), so a restart restores the variants instead of only the seeded `boot` variant. A module without PostgreSQL keeps the registry in memory only.
- The module-wide `TextSplitter` and `VectorStorePort` beans are thin delegates (`ActiveTextSplitter`, `ActiveVectorStore`) that resolve the active variant per call. A switch therefore applies to the next ingestion/query without a restart and requires no change to ingestion, chat, or retrieval code.
- Every runnable module exposes `GET/POST /api/modules/{module}/variants`, `GET …/active`, `GET/PUT/DELETE …/{variantId}`, and `POST …/{variantId}/activate`.
- The TUI exposes `variant [<variant-id> | add [<strategy>] [<size>] [<overlap>]]`.
- Validation is server-side: `size > 0`, `0 <= overlap < size`, and for `TOKEN` `size / 4 > overlap / 4` (a zero-advance window would loop forever).
- **A variant embeds with its own model.** The module sends the variant's `embeddingModel` on `/api/embed`; `rag-provider` builds and caches a client for that model on the active embedding provider, so two variants can use different dimensions at once. The globally switched model (`connect embedding`) stays the fallback for the sealed `boot` variant and for requests without a model.
- `SEMANTIC` cuts on topic boundaries: the document's sentences are embedded with the variant's model and a new chunk starts where the cosine distance to the next sentence reaches the 95th percentile of that document's distances. `chunkSize` bounds the sentence grouping and an oversized sentence is still split; because the previous chunk's tail is prepended, a chunk can exceed `chunkSize` by up to `chunkOverlap`.

## Consequences

### Positive

- Chunking strategies and embedding models can be compared on one corpus without restarts, global model switches, or mixed vectors.
- Per-variant storage mirrors the per-module schema isolation of ADR-0006.
- Switching stays transparent to ingestion/chat/retrieval because only two port beans change behavior.

### Negative

- Each variant adds a pgvector table. Deleting a variant drops it, so orphan tables only remain if the drop fails (logged) or the variant predates this behaviour.
- A variant's model has to exist on the active embedding provider: the provider resolves the model id against the active provider profile, so a model from another provider fails at embed time instead of silently falling back.
- Variants survive a restart through the module's `pipeline_variants` table, but activation still cannot cross modules, and a module without PostgreSQL loses non-boot variants on restart.
- A `SEMANTIC` ingestion embeds every sentence before embedding the chunks, so it costs roughly one extra embedding per sentence (≈4.5x the recursive strategy's calls on this repo's docs) and is the slowest strategy: large documents are better ingested through `AsyncIngestionService`.
