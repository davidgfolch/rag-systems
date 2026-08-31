# rag-evaluation

Metrics, benchmarking, and comparison tools for scoring and comparing RAG implementations.

## Purpose

Provide quantitative evidence for comparing the different RAG modules (rag-basic, rag-advanced, rag-agentic) and chunking/embedding/retrieval strategies.

## Metrics

| Metric | Definition |
|--------|------------|
| Precision@k | Fraction of retrieved chunks that are relevant |
| Recall@k | Fraction of relevant chunks retrieved |
| MRR | Mean Reciprocal Rank of first hit |
| Answer relevance | LLM-as-judge score of answer quality |
| Faithfulness | Groundedness of answer to retrieved context |
| Latency | Per-pipeline-stage timing (via observability) |
| Cost | Estimated token cost per query |

## Structure

```
com.rag.evaluation
├── metrics/    - Metric calculators
├── benchmark/  - Benchmark harness, test corpora
└── comparison/ - Compare module results, generate reports
```

## Usage

```bash
# Run benchmark against all configured store/strategy combos
.\scripts\run.bat rag-evaluation --args "--benchmark"

# Generate comparison report
.\scripts\run.bat rag-evaluation --args "--report"
```

## Benchmark Guidance

- Use the SAME document corpus and queries across strategies for fair comparison
- Run multiple passes, take medians
- Compare chunking strategies: fixed vs recursive vs semantic
- Compare providers: local (Ollama) vs cloud (OpenAI)

## Related

- [performance-metrics.md](../../docs/comparison/performance-metrics.md)
- [trade-offs.md](../../docs/comparison/trade-offs.md)

## Testing

```bash
.\scripts\test.bat rag-evaluation
```