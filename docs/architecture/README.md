# Architecture

Documentation on the RAG Systems architecture — layered design, module roles, and key decisions.

| Document | Purpose |
|----------|---------|
| [Overview](overview.md) | Layered architecture, module roles, pipeline diagrams, provider abstraction |
| [Decision Records](decision-records/) | Architecture Decision Records (ADRs) for significant design choices |
| [Master Plan](../PLAN.md) | Full architecture plan, technology stack, implementation phases |

## Key Concepts

- **Layered Architecture per module**: domain → services → repositories → config
- **Strategy Pattern**: interchangeable chunking, embedding, and retrieval via interfaces
- **Provider Abstraction**: swap OpenAI / Ollama / HuggingFace via configuration, not code
- **Repository Pattern**: `VectorStore` interface abstracts PgVector and in-memory stores
- **Composition over Inheritance**: pipelines compose strategy implementations
