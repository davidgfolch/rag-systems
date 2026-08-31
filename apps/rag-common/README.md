# rag-common

Shared core library for all RAG modules. Provides domain models, service interfaces, and repository abstractions that every RAG architecture uses.

## Purpose

Centralize shared concepts (DRY) so rag-basic, rag-advanced, and rag-agentic don't duplicate domain logic. Follows DDD with a rich domain model and clean interfaces (Strategy pattern).

## Layers

```
com.rag.common
├── domain/       - Document, Chunk, Embedding entities (depend on nothing)
├── services/     - DocumentParser, TextSplitter, EmbeddingModel interfaces
├── repositories/ - VectorStore interface (data access abstraction)
└── config/       - Spring configuration helpers
```

## Key Interfaces

| Interface | Purpose | Swappable implementations |
|-----------|---------|--------------------------|
| `DocumentParser` | Parse PDF/DOCX/HTML to text | Tika, JSoup |
| `TextSplitter` | Chunk text for embedding | Fixed, recursive, semantic, agentic |
| `EmbeddingModel` | Generate embeddings | Ollama, OpenAI, HuggingFace |
| `VectorStore` | Store + similarity search embeddings | PgVector, SimpleVectorStore |

## Dependency Rules

- `domain` depends on nothing
- `services` depends on `domain` only
- `repositories` depends on `domain` + Spring Data
- No circular dependencies

## Testing

```bash
.\scripts\test.bat rag-common
```