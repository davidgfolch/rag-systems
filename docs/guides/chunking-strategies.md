# Chunking Strategies for RAG

This guide explains the chunking strategies implemented across the RAG modules and how to choose between them. **The quality of your retrieval is determined more by how you chunk than by which embedding model you use.**

## Why Chunking Matters

LLMs have context windows and embeddings lose precision over large blocks of text. Documents must be split into chunks that:
1. Fit within the embedding model's context window
2. Preserve semantic meaning (don't break concepts mid-sentence)
3. Are small enough for precise retrieval but large enough for context

A naive split can break semantics: `"Configure the datasource URL in application.properties. En"` / `"sure the driver class is set to..."` - a query about "driver class" retrieves the second chunk but loses the context about which file/config.

## Strategy Comparison

| Strategy | Best For | Retrieval Precision | Cost | Complexity |
|----------|----------|---------------------|------|-----------|
| Fixed-size sliding window | Homogeneous text (reviews, logs) | ~71% | Low | Low |
| Recursive character | Most production workloads | ~82% | Low | Low |
| Semantic | High-value documents | ~91% | High (8x) | Medium |
| Agentic | Complex/varied documents | Best potential | High | High |

*Precision figures from a benchmark on a 500-document technical corpus (see comparison docs).*

## Strategy Details

### 1. Fixed-Size Sliding Window (Baseline)

Splits text into fixed-size chunks with overlap.

```java
TokenTextSplitter.builder()
    .withChunkSize(512)        // target tokens per chunk
    .withOverlap(128)          // ~25% context overlap
    .withMinChunkSizeChars(5)  // discard tiny trailing fragments
    .build();
```

**Best for**: Homogeneous, low-stakes text where structure doesn't matter.
**Pros**: Fast, cheap, simple. **Cons**: Poor semantic coherence.

### 2. Recursive Character Splitter (Recommended Default)

Tries paragraph → sentence → word boundaries in order, only splitting finer when needed. Produces semantically coherent chunks far more often than fixed-size.

**Best for**: Most production workloads. **Pros**: Good balance of cost/precision. **Cons**: May split mid-sentence occasionally.

### 3. Semantic Chunking

Uses embeddings to detect topic shifts - breaks text into chunks were semantic similarity drops. Includes contextual/summarization variants.

**Best for**: High-value, complex documents (legal, research, manuals) where precision outweighs ingestion cost.
**Pros**: Best precision. **Cons**: ~8x ingestion cost (per-sentence embedding calls).

### 4. Agentic Chunking

An AI agent evaluates the document and user intent to decide how to chunk - may use different strategies per section.

**Best for**: Documents varying widely in type/structure. **Cons**: Complex, expensive, slower.

## Chunk Size Guidance

| Content Type | Chunk Size (tokens) | Notes |
|--------------|---------------------|-------|
| Prose / general docs | 300-512 | Sweet spot for most embedding models |
| Code documentation | 256-384 | Function-level boundaries |
| API references | 256-384 | Function-level boundaries, metadata tags |
| Tables/data | Preserve as units | Don't split tabular data |

Smaller chunks (256-384) are better for code; larger (512) for prose. Match your chunk size to your embedding model's window.

## Implementation (Spring AI)

Spring AI provides `TextSplitter` implementations that are swappable via the Strategy pattern. The `TextSplitter` interface in `rag-common` lets modules swap strategies via configuration.

```java
// Inject via interface
@Service
public class IngestionService {
    private final TextSplitter textSplitter; // Swappable via config

    public List<Chunk> ingest(Document doc) {
        return textSplitter.split(doc);
    }
}
```

## Choosing a Strategy

- **Small/personal corpus, learning**: fixed-size or recursive - fast and good enough
- **Production docs, mixed structure**: recursive (default) + metadata filtering
- **Critical retrieval accuracy**: semantic chunking on the most important documents
- **Benchmarking**: run rag-evaluation to compare strategies quantitatively

## Related

- [Performance Metrics](../comparison/performance-metrics.md)
- [Trade-offs](../comparison/trade-offs.md)
