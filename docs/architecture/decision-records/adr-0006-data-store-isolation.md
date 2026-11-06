# ADR-0006: Data Store Isolation - Schema per Module + `documentId` Metadata

- **Status**: Accepted
- **Date**: 2026-09-01

## Context

Each rag-* implementation is a bounded context with its own approach to chunking, retrieval, and generation. If they all wrote to the same pgvector table, documents ingested through one module would contaminate retrieval in another, making comparisons meaningless. The switchable-TUI requirement means modules run concurrently against one shared PostgreSQL instance.

## Decision

Isolate data per module at the **PostgreSQL schema** level, with a single `chunks` table per module schema:

- rag-basic → schema `rag_basic`, table `chunks`
- rag-advanced → schema `rag_advanced`, table `chunks`
- rag-agentic → schema `rag_agentic`, table `chunks`
- rag-memory → schema `rag_memory` (non-vector: conversations/messages)

Per-document granularity comes from the `documentId` key inside the chunk `metadata` JSONB column, because Spring AI's pgvector table has no separate document column.

Configuration uses Spring AI PgVectorStore properties in each module's `application.yml`:

- `spring.ai.vectorstore.pgvector.schema-name` — pinned per module from its own variable: `PGVECTOR_BASIC_SCHEMA=rag_basic`, `PGVECTOR_ADVANCED_SCHEMA=rag_advanced`, `PGVECTOR_AGENTIC_SCHEMA=rag_agentic`. A single shared `PGVECTOR_SCHEMA` is not allowed: the last-started module would then write into another module's schema.
- `spring.ai.vectorstore.pgvector.table-name: chunks`
- `spring.ai.vectorstore.pgvector.schema-validation: true`

Schema creation is idempotent via `initialize-schema: true`. Deep retrieval at the document level is supported by filtering on `metadata->>'documentId'` (`PgVectorStoreAdapter` writes that key on ingestion). Since [ADR-0015](adr-0015-pipeline-variants.md), the `boot` variant keeps this `chunks` table and runtime variants own `variant_<variantId>` tables in the same module schema.

## Consequences

### Positive
- Clean logical isolation per module without separate databases or table explosion
- Each module can be reset independently (drop schema) without affecting others
- `documentId` metadata filtering enables per-document query scoping
- One Postgres container keeps local "fits on a laptop" constraint

### Negative
- Complexity of reading across modules is not supported (by design - modules are compared side by side)
- Requires re-ingesting content into a second module's schema to compare approaches on the same documents

## Related

- [ADR-0008: rag-memory Module](adr-0008-rag-memory.md)