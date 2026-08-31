# Vector Stores for RAG

This guide covers the vector store options in the RAG systems, their trade-offs, and how to choose.

## What is a Vector Store?

A vector store stores document embeddings (high-dimensional vectors) and supports **similarity search** - finding vectors closest to a query vector per cosine similarity / distance.

## Options

### SimpleVectorStore (In-Memory)

Spring AI's built-in in-memory vector store.

| Attribute | Detail |
|-----------|--------|
| **Best for** | Learning, small corpora, tests |
| **Speed** | Fastest (in-memory) |
| **Persistence** | None (lost on restart) |
| **Scale** | Small (hundreds of chunks) |
| **Setup** | Zero |

### PgVector (PostgreSQL extension)

Vector storage inside PostgreSQL via the pgvector extension.

| Attribute | Detail |
|-----------|--------|
| **Best for** | Most local development & production |
| **Speed** | Fast (indexed) |
| **Persistence** | Yes - survives restarts |
| **Scale** | Medium to large |
| **Setup** | Docker container (in repo) |
| **Extra** | Combine vector + relational metadata filtering |

This is the default. Runs locally on CPU, no GPU needed.

## Choosing

| Scenario | Choose |
|----------|--------|
| Learning / tests / tiny corpus | SimpleVectorStore |
| Local dev with persistence | PgVector |
| Production relational + vector | PgVector |
| Very large scale / special ops | Add Qdrant/Weaviate later |

## Abstraction

All stores implement the `VectorStore` interface in `rag-common`. Swap via configuration, not code:

```yaml
# application.yml
rag:
  vector-store:
    type: pgvector   # or simple
```

```java
Service
public class RetrievalService {
    private final VectorStore vectorStore; // Swappable

    public List<Document> search(String query, int topK) {
        return vectorStore.similaritySearch(query, topK);
    }
}
```

## Related

- [Getting Started](../guides/getting-started.md)
- [Trade-offs](../comparison/trade-offs.md)
