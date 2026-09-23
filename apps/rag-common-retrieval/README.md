# rag-common-retrieval

Shared **vector store** implementations behind the `VectorStorePort` strategy interface. A **library** — no runnable app, no HTTP port. Consumed by rag-basic, rag-advanced, and rag-tui.

## Purpose

Provide swappable storage + similarity search so the `rag-*` modules can move between an in-memory store and PostgreSQL/PgVector purely by configuration (`VECTOR_STORE_TYPE=simple|pgvector`), with no code changes.

## Contents

```
com.rag.common.retrieval
└── store/
    ├── InMemoryVectorStore     - custom in-memory cosine-similarity store (default in rag-tui)
    └── PgVectorStoreAdapter    - PgVector-backed store with schema isolation
```

## PgVectorStoreAdapter

The production backend. Key behaviors:

- **Per-module schema isolation** — each `rag-*` module pins its own PostgreSQL schema (`rag_basic`, `rag_advanced`, ...) so corpora never contaminate each other. The schema name comes from `PGVECTOR_SCHEMA`; when blank it is derived from the active embedding dimension (`rag_<dim>`).
- **Per-document query scoping** — each chunk stores a `documentId` metadata key, so a query can be scoped to one ingested document. (See [ADR-0006](../../docs/architecture/decision-records/adr-0006-data-store-isolation.md).)
- **Schema auto-init** — on startup it validates/creates the `chunks` table (`schema-validation: true`, `initialize-schema: true`).

## Configuration

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `rag.vector-store.type` | `VECTOR_STORE_TYPE` | `pgvector` | `pgvector` or `simple` |
| `spring.ai.vectorstore.pgvector.schema-name` | `PGVECTOR_SCHEMA` | `rag_basic` in `.env` | Per-module schema (blank → `rag_<dimension>`) |
| `spring.ai.vectorstore.pgvector.table-name` | - | `chunks` | Storage table |
| `spring.datasource.*` | `PGVECTOR_HOST/PORT/DB/USER/PASSWORD` | `localhost:5432/rag` | Postgres connection |

## Why a separate module?

Retrieval backends are the most likely thing to swap (local PgVector → cloud vector DB). Isolating them behind the `VectorStorePort` and keeping the code in one module means a new backend is a new adapter here, not a change across every RAG app. The repository pattern is also enforced by architecture tests (domain/services never touch the store directly).

## Testing

```bash
.\scripts\test.bat rag-common-retrieval
.\scripts\test.bat rag-common-retrieval --coverage
```

## Related

- [Vector stores guide](../../docs/guides/vector-stores.md)
- [ADR-0006: Data store isolation](../../docs/architecture/decision-records/adr-0006-data-store-isolation.md)
- [rag-common-core](../rag-common-core/README.md) — `VectorStorePort`, `MetadataKeys.documentId`
- [rag-provider](../rag-provider/README.md) — dimension rules when switching embedding models