# ADR-0001: Monorepo Layout with Decoupled Modules

- **Status**: Accepted
- **Date**: 2026-08-31

## Context

The project must implement multiple RAG architectures (basic, advanced, agentic) with different chunking/embedding/search approaches for comparison. These need to be decoupled so each can evolve independently, possibly with different tech stacks.

## Decision

Use a **Maven multi-module monorepo** with a shared `rag-common` library and decoupled runnable modules in `apps/`:

- `rag-common` - shared domain models, interfaces (DocumentParser, TextSplitter, EmbeddingModel, VectorStore)
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
- Requires building rag-common first in the dependency chain
- Monorepo needs consistent versioning via parent POM
