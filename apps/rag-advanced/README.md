# rag-advanced

Production-grade RAG with reranking, hybrid search, semantic chunking, and metadata filtering.

## Purpose

Extend rag-basic with techniques that materially improve retrieval quality: two-stage retrieval (fast search + cross-encoder rerank), hybrid vector+keyword search, and metadata-aware filtering.

## Layers

```
com.rag.advanced
├── reranking/    - LLM / cross-encoder rerankers
├── hybrid/       - Vector + keyword (BM25) hybrid search
├── metadata/     - Metadata filtering on retrieved chunks
├── retrieval/    - Query transformation, contextual compression
└── api/          - REST controllers
```

## Advanced Features

| Feature | Description |
|---------|-------------|
| Reranking | Re-score top-K candidates for precision |
| Hybrid search | Combine semantic + lexical for robustness |
| Metadata filtering | Filter by source, doc_type, language, date |
| Query transformation | Expand/rewrite queries |
| Contextual compression | Reduce noise in retrieved context |

## Running

```bash
.\scripts\run.bat rag-advanced --profile local
```

## Comparing with rag-basic

Use [rag-evaluation](../rag-evaluation/README.md) to measure the retrieval-quality benefit of these advanced techniques against the rag-basic baseline.

## Testing

```bash
.\scripts\test.bat rag-advanced
.\scripts\test.bat
```