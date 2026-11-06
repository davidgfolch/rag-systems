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
| Semantic | High-value documents | ~91% (literature) | High (~4.5x recursive embedding calls) | Medium |
| Agentic | Complex/varied documents | Best potential | High | High |

*Precision figures are literature references — this repository has no retrieval-precision benchmark yet. The semantic **cost** is measured here: 4.5x the recursive strategy's embedding calls on this repo's `docs/` corpus (see [performance metrics](../comparison/performance-metrics.md)).*

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

Implemented as `SemanticChunker` in `rag-common-ingestion`. It splits the document into sentences, embeds them with the variant's own model, and starts a new chunk wherever the cosine distance to the next sentence reaches the 95th percentile of that document's distances — a topic shift. `chunkSize` caps the sentence grouping: a group that would outgrow the window is cut anyway and a single oversized sentence is hard-split. With `overlap > 0` the previous chunk's tail is then prepended to the next chunk, so a chunk can exceed `chunkSize` by up to `chunkOverlap` characters — the window bounds the grouping, not the final chunk text.

**Best for**: High-value, complex documents (legal, research, manuals) where precision outweighs ingestion cost.
**Pros**: Boundaries follow the text instead of a fixed separator, so concepts are less likely to be cut in half. **Cons**: every sentence is embedded before the chunks are, so ingestion costs ~4.5x the recursive strategy's embedding calls on this repo's docs corpus (≈1,850 against ≈410; the count moves as the docs corpus changes), and `IngestionService` is synchronous — large documents belong on `AsyncIngestionService`.

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

## Format-Aware Chunking

Parsing preserves structure, and chunking consumes it. `DocumentParser.parse(Document)` returns a `ParsedDocument` whose ordered `DocumentBlock`s retain their type (heading, paragraph, table, code, list, quote, JSON record), section path, and provenance (page, DOM path, JSON path, offsets). Chunking then runs in two stages:

1. **Group blocks** — `SectionAwareBlockGrouper` flushes a group when the section, page, or character window changes; tables and code form their own protected groups. `JsonBlockGrouper` groups sibling JSON records up to `maxRecordsPerChunk`.
2. **Split each group** — `StructuralDocumentChunker` hands the group to the variant's existing `TextSplitter`, so the established algorithms still decide the final character/token windows.

`FormatAwareChunkerDispatcher` routes a `ParsedDocument` to the chunker registered for its `SourceType`. The resulting chunk metadata carries `format`, `contentType`, `sectionPath`, `page`, `domPath`, `jsonPath`, `startOffset`, `endOffset`, `codeLanguage`, and `tableHeader`, which is what the query APIs expose for citations.

Parsing is selected with `rag.parsing.mode`: `auto` (structured, the default), `tika` (legacy flat extraction), or `plain` (passthrough). No parser silently falls back on a broken document; only an unregistered format goes to plain text.

## Implementation

The chunking interfaces live in `rag-common-core` and are swappable via configuration:

```java
public interface DocumentChunker {
    boolean supports(ParsedDocument document);
    List<Chunk> chunk(ParsedDocument document, ChunkingConfig config);
}
```

`IngestionService` injects a `DocumentChunker` (the module-wide `ActiveDocumentChunker` resolves the active variant per call), so a variant switch changes the structural chunking of the next ingestion without a restart. `TextSplitter` remains the low-level splitting port used inside each structural group and for the legacy code path.

## Choosing a Strategy

- **Small/personal corpus, learning**: fixed-size or recursive - fast and good enough
- **Production docs, mixed structure**: recursive (default) + metadata filtering
- **Critical retrieval accuracy**: semantic chunking on the most important documents
- **Benchmarking**: run rag-evaluation to compare strategies quantitatively

## Related

- [Performance Metrics](../comparison/performance-metrics.md)
- [Trade-offs](../comparison/trade-offs.md)
