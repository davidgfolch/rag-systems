# ADR-0006: Data Store Isolation - Schema per Module + `document_id` Column

- **Status**: Accepted
- **Date**: 2026-09-01

## Context

Each rag-* implementation is a bounded context with its own approach to chunking, retrieval, and generation. If they all wrote to the same pgvector table, documents ingested through one module would contaminate retrieval in another, making comparisons meaningless. The switchable-TUI requirement means modules run concurrently against one shared PostgreSQL instance.

## Decision

Isolate data per module at the **PostgreSQL schema** level, with a single `chunks` table per module schema and a `document_id` column for per-document granularity:

- rag-basic → schema `rag_basic`, table `chunks`
- rag-advanced → schema `rag_advanced`, table `chunks` (future)
- rag-agentic → schema `rag_agentic`, table `chunks` (future)
- rag-memory → schema `rag_memory` (non-vector: conversations/messages)

Configuration uses Spring AI PgVectorStore properties in each module's `application.yml`:

- `spring.ai.vectorstore.pgvector.schema-name`
- `spring.ai.vectorstore.pgvector.table-name`
- `spring.ai.vectorstore.pgvector.schema-validation: true`

Schema creation is idempotent via `initialize-schema: true`. Deep retrieval at the document level is supported by filtering on the `document_id` column (already written as chunk metadata by `PgVectorStoreAdapter`).

## Consequences

### Positive
- Clean logical isolation per module without separate databases or table explosion
- Each module can be reset independently (drop schema) without affecting others
- `document_id` filtering enables per-document query scoping
- One Postgres container keeps local "fits on a laptop" constraint

### Negative
- Complexity of reading across modules is not supported (by design - modules are compared side by side)
- Requires re-ingesting content into a second module's schema to compare approaches on the same documents

## Related

- [ADR-0008: rag-memory Module](adr-0008-rag-memory.md)