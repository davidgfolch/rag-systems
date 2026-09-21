# ADR-0001: Monorepo Layout with Decoupled Modules

- **Status**: Accepted
- **Date**: 2026-08-31

## Context

The project must implement multiple RAG architectures (basic, advanced, agentic) with different chunking/embedding/search approaches for comparison. These need to be decoupled so each can evolve independently, possibly with different tech stacks.

## Decision

Use a **Maven multi-module monorepo** with shared `rag-common-*` libraries and decoupled runnable modules in `apps/`:

- `rag-common-core`, `rag-common-ingestion`, `rag-common-retrieval`, `rag-common-generation` - shared domain models, interfaces (DocumentParser, TextSplitter, EmbeddingModelPort, ChatModelPort, VectorStorePort) and strategy implementations, split by capability (see [ADR-0013](adr-0013-rag-common-split.md))
- `rag-basic`, `rag-advanced`, `rag-agentic` - distinct RAG bounded contexts
- `rag-observability`, `rag-evaluation`, `rag-cli` - cross-cutting concerns

All operations centralized in `scripts/` (install/test/build/run/docker). Shared interfaces use the Strategy pattern so each module swaps implementations via config.

## Consequences

### Positive
- Each RAG architecture is an independent bounded context (DDD)
- Shared interfaces prevent duplication (DRY)
- Easy to compare approaches quantitatively via rag-evaluation
- Future modules can use different tech stacks

### Negative
- Requires building the rag-common-* libraries first in the dependency chain
- Monorepo needs consistent versioning via parent POM
