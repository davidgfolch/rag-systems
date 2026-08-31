# rag-basic

The **baseline** RAG implementation: document ingestion with fixed/recursive chunking and basic vector similarity search. This is the educational reference implementation.

## Purpose

Demonstrate the core RAG pipeline (Ingest → Chunk → Embed → Store → Retrieve → Generate) with simple, understandable components. Use as the baseline against which rag-advanced and rag-agentic are compared.

## Layers

```
com.rag.basic
├── ingestion/    - Document ingestion from PDF, DOCX, HTML
├── chunking/     - Fixed-size, recursive, token-based splitters
├── embedding/    - Embedding generation (via rag-common interface)
├── vectorstore/  - PgVector + in-memory stores
├── retrieval/    - Similarity search + prompt augmentation
└── api/          - REST controllers (Swagger)
```

## Chunking Strategies

| Strategy | When to use |
|----------|-------------|
| Fixed-size sliding window | Homogeneous text, baseline |
| Recursive character | Most cases, default |
| Token-based | Match embedding model windows |

## Running

```bash
# Local profile (Ollama)
.\scripts\run.bat rag-basic --profile local

# Cloud profile (OpenAI)
.\scripts\run.bat rag-basic --profile cloud
```

API: http://localhost:8080
Swagger UI: http://localhost:8080/swagger-ui.html

## Configuration

- `VECTOR_STORE_TYPE=pgvector|simple` - storage backend
- `CHUNK_SIZE`, `CHUNK_OVERLAP` - chunking defaults
- `SPRING_PROFILES_ACTIVE=local|cloud` - provider selection

## Performance Notes

- Embeddings run fast on CPU (nomic-embed-text, ~580 chunks/sec)
- Generation is the only GPU-hungry layer; use small models on CPU
- See [performance metrics](../../docs/comparison/performance-metrics.md)

## Testing

```bash
.\scripts\test.bat rag-basic
.\scripts\test.bat rag-basic --coverage
.\scripts\test.bat --architecture
```