# RAG Architecture Trade-offs

This document captures the key architectural trade-offs across the RAG implementations.

## Local vs Cloud Providers

| Aspect | Local (Ollama) | Cloud (OpenAI/Claude) |
|--------|----------------|------------------------|
| **Cost** | Zero marginal | Per-token |
| **Privacy** | Data stays local | Data leaves machine |
| **Quality** | Good (small models) | Frontier (best) |
| **Latency** | Slower on CPU | Fast |
| **Offline** | Yes | No |
| **Hardware** | Needs local compute | None |
| **Best for** | Learning, private data, low volume | Complex reasoning, production |

**Hybrid approach**: local retrieval (nomic) + cloud generation (gpt-4o) balances cost and quality.

## Chunking Trade-offs

| Strategy | Precision | Cost | When to use |
|----------|-----------|------|-------------|
| Fixed-size | Low (~71%)† | Lowest | Homogeneous, low-stakes |
| Recursive | Good (~82%)† | Low | Default for most |
| Semantic | Best (~91%)† | ~4.5x recursive embedding calls‡ | High-value documents |
| Agentic | Best potential | High | Complex/varied docs |

† **Precision figures are literature references.** Nothing in this repository measures retrieval precision yet (`rag-evaluation` is planned), so ~71/82/91% come from published chunking comparisons.

‡ **The semantic cost is measured here.** Over the 33 markdown files in `docs/` at a 512/64 window, a semantic ingestion needs 1,820 embedding calls (1,197 sentence + 623 chunk embeddings) against 401 for recursive — 4.5x — because it embeds every sentence *and* yields more, smaller chunks (18.9 vs 12.2 per document). The timing used a local stand-in model, not a provider; see [Performance Metrics](performance-metrics.md) and `ChunkingCostComparisonTest` ([ADR-0015](../architecture/decision-records/adr-0015-pipeline-variants.md)).

## Vector Store Trade-offs

| Store | Persistence | Speed | Scale | Setup |
|-------|-------------|-------|-------|-------|
| SimpleVectorStore | None | Fastest | Small | None |
| PgVector | Yes | Fast | Large | Docker |

## Simple vs Advanced vs Agentic RAG

| Module | Retrieval quality | Complex reasoning | Complexity | Cost |
|--------|-------------------|-------------------|------------|------|
| rag-basic | Good | No | Low | Low |
| rag-advanced | High (rerank, hybrid) | No | Medium | Medium |
| rag-agentic | High | Yes (multi-step) | High | High |

## Reactive/Parallel Trade-offs

| Approach | Best for | Complexity |
|----------|----------|------------|
| Synchronous | Simple flows | Lowest |
| Virtual threads | Blocking I/O (LLM calls) | Low |
| Project Reactor | Composable async | Medium |
| Parallel streams | CPU-bound chunking | Low |

Use reactive/parallel **only when the performance benefit is clear** (I/O-bound, independent tasks). Avoid over-engineering simple synchronous flows.

## Observability Overhead

Tracing and metrics add minimal overhead (<2% typically). For very high-throughput production, reduce sampling from 100% to 10%.

## Architecture Decision Records

See [decision-records](../architecture/decision-records/) for detailed ADRs on major decisions.

## Related

- [Performance Metrics](performance-metrics.md)
- [Architecture Overview](../architecture/overview.md)
