# Performance Metrics & Local Machine Guide

This document details performance considerations, quantitative metrics, and how the system runs on a **regular local machine**.

## Can it run locally? Yes.

RAG separates into layers with very different hardware demands. The heavy compute (large LLM) is **composable and optional**. You can run retrieval fully locally on CPU with small models, and only the optional "generation" layer benefits from a GPU.

## Layer Hardware Requirements

### Embeddings (retrieval engine) - CPU-friendly
Embedding models are tiny and run fast on CPU. **No GPU required.**

| Model | Params | Memory | CPU Speed | Notes |
|-------|--------|--------|-----------|-------|
| `all-MiniLM-L6-v2` | ~22M | ~90MB | ~100+ chunks/sec | Smallest, fastest |
| `nomic-embed-text` | 137M | ~300MB | ~580 chunks/sec | Great default, 8k context |
| `mxbai-embed-large` | 335M | ~700MB | slower | Higher quality |
| `bge-m3` | ~570M | ~1.2GB | noticeable | Multilingual |

> CPU throughput: `nomic-embed-text-v2` dominates at ~580 chunks/sec, re-indexing a 5K-page corpus in ~9 min (vs ~55 min for slower models).

### Vector Store - CPU-friendly
PgVector and in-memory stores run entirely on CPU. This stage is memory-bandwidth bound.

### LLM Generation - the demanding part (optional to scale)
This is the only layer that benefits significantly from a GPU.

| Model | Size | CPU (16GB RAM) | GPU (8GB VRAM) |
|-------|------|----------------|----------------|
| Phi-4 / Qwen3 4B | ~4GB | Usable, slow | Fast |
| Qwen3 8B (Q4_K_M) | 4-8GB | Too slow | Comfortable |
| Qwen3-30B-A3B (MoE) | ~16GB | No | Excellent (needs 16GB) |

> On CPU, generation can be several seconds/token for 7B+ models. For a snappy local experience, use 3B-4B models on CPU, or any modest GPU.

## Recommended Local Configurations

### Minimum (CPU-only, 16GB RAM)
- Embeddings: `nomic-embed-text`
- Vector store: PgVector (in-memory fallback)
- LLM: `phi4` or `qwen3:4b` (usable but slow generation)

### Recommended (CPU + 8GB GPU)
- Embeddings: `nomic-embed-text`
- Vector store: PgVector
- LLM: `qwen3:8b` (Q4_K_M) - comfortable, 128k context

### Multilingual documents
- Embeddings: `bge-m3`

## Ingestion vs Query Performance

| Metric | CPU | GPU |
|--------|-----|-----|
| Indexing 5K pages (nomic) | ~9 min | ~1 min |
| Embedding throughput (nomic) | ~580 chunks/sec | ~4,800 chunks/sec |
| Typical chunk per doc | 10-50 | - |

- **Ingestion** is a one-time cost per document - acceptable to be slower on CPU
- **Query** retrieval (embed + vector search) is fast on CPU; only generation is slow

## Benchmarking Methodology

Use `rag-evaluation` to benchmark strategies. Track:
- **Retrieval precision / recall @k**
- **MRR** (Mean Reciprocal Rank)
- **Answer relevance** (LLM-as-judge)
- **Faithfulness** (groundedness to retrieved context)
- **Latency** per pipeline stage (via observability)
- **Cost** per query (OpenAI vs local)

See [Observability](../guides/observability.md) for metric collection.

## Chunking Strategy Benchmark (500-doc technical corpus)

| Strategy | Precision | Ingestion Cost | Notes |
|----------|-----------|----------------|-------|
| Fixed-size | ~71% | Low | Fast, cheap |
| Recursive | ~82% | Low | Best default |
| Semantic | ~91% | 8x | High precision, expensive |

## Cost & Privacy Trade-off

- **Local (Ollama)**: Zero marginal cost, private, offline. Lower quality reasoning (small models).
- **Cloud (OpenAI)**: Frontier model quality, per-token cost, data leaves machine.
- **Hybrid**: Local retrieval (nomic) + cloud generation (gpt-4o) - balances cost and quality.

## Related

- [Trade-offs](trade-offs.md)
- [Observability](../guides/observability.md)
- [Chunking Strategies](../guides/chunking-strategies.md)
