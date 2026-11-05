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

## Chunking Strategy Benchmark

### Measured here: ingestion embedding cost

Measured by `ChunkingCostComparisonTest` over this repository's own `docs/` corpus (33 markdown files, ~150 KB, window 512/64; the token chunker runs 128/16 tokens) with a local hashing embedding model standing in for a provider. One "embedding call" is one text handed to the embedding port — a provider adapter may batch many texts into a single HTTP request. The timings are chunking on CPU and contain no network latency.

| Strategy | Chunks | Chunks/doc | Embedding calls | vs recursive | Split time (33 docs) |
|----------|--------|------------|-----------------|--------------|----------------------|
| Fixed-size | ≈350 | ≈10.5 | ≈350 | 0.8x | 3-7 ms |
| Recursive | ≈410 | ≈12.5 | ≈410 | 1.0x | 17-28 ms |
| Token | ≈190 | ≈5.8 | ≈190 | 0.5x | 16-21 ms |
| Semantic | ≈630 | ≈19.2 | ≈1,850 (≈1,220 sentences + ≈630 chunks) | ≈4.5x | 30-38 ms |

`IngestionService` embeds each chunk once, so a strategy's ingestion cost is `chunks` — except semantic, which also embeds every sentence to find its boundaries (`sentences + chunks`). Semantic additionally produces more, smaller chunks than recursive (≈19 vs ≈12.5 per document), so the measured premium is **≈4.5x, not the often-quoted 8x**. The ratio holds at the default 512/128 window as well, because overlap changes chunk text, not sentence grouping. Ingestion is synchronous: for large documents use `AsyncIngestionService`.

> The figures are approximate on purpose: the corpus **is** `docs/`, so every edit under `docs/` shifts the counts by a few percent and a table measured against an earlier tree will not reproduce exactly. Re-run `mvn -pl apps/rag-common-ingestion -am test -Dtest=ChunkingCostComparisonTest` for the current values; the test asserts only that semantic costs more than 2x recursive, so it stays green when the corpus moves.

### Precision (literature, not measured here)

| Strategy | Precision | Source |
|----------|-----------|--------|
| Fixed-size | ~71% | published chunking comparisons |
| Recursive | ~82% | published chunking comparisons |
| Semantic | ~91% | published chunking comparisons |

> **No retrieval-precision run exists in this repository**: `rag-evaluation` is still planned, so these three figures are literature references, not measurements from here. Only the cost table above is measured ([PLAN.md Phase 8](../PLAN.md#phase-8-semantic-chunking-adr-0015-semantic-variant)).

## Cost & Privacy Trade-off

- **Local (Ollama)**: Zero marginal cost, private, offline. Lower quality reasoning (small models).
- **Cloud (OpenAI)**: Frontier model quality, per-token cost, data leaves machine.
- **Hybrid**: Local retrieval (nomic) + cloud generation (gpt-4o) - balances cost and quality.

## Related

- [Trade-offs](trade-offs.md)
- [Observability](../guides/observability.md)
- [Chunking Strategies](../guides/chunking-strategies.md)
