# ADR-0016: Format-aware parsing and structure-aware chunking

- **Status**: Accepted
- **Date**: 2026-11-06

## Context

Ingestion previously reduced every source to a single flat string and split it with a character window. That discarded the information the source already carried: PDF page boundaries, DOCX headings, HTML section hierarchy, Markdown code fences and tables, and JSON record boundaries. The result was chunks that cut across structures, citations that could not say which page or section a passage came from, and no way to preserve tables or code blocks intact. Ingestion also lost the distinction between *how* a document was delivered (`file` vs `web`) and *what* format it actually is.

## Decision

- **Parsed documents are structured.** `DocumentParser.parse(Document)` returns a `ParsedDocument`: `{documentId, sourceType, metadata, blocks}` where each `DocumentBlock` carries `{type, content, sectionPath, location, metadata}`. `SourceLocation` holds flat provenance (`page`, `domPath`, `jsonPath`, `startOffset`, `endOffset`).
- **The parser port evolves in place** rather than adding a parallel interface. `supports(Document)` has a default, so existing simple parsers stay usable as fallbacks. The blast radius was two implementations, one consumer, and their tests.
- **One parser per format**, dispatched by a `FormatAwareDocumentParser` that detects the format from `format`/`fileName` metadata and the content itself:
  - Markdown → commonmark + GFM tables (headings, lists, fenced code with language, tables, quotes).
  - HTML → jsoup (title, headings, paragraphs, lists, tables, code, quotes with `domPath`).
  - JSON → Jackson, one block per array record or nested object with its `jsonPath`; invalid JSON fails instead of being flattened.
  - PDF/DOCX → Tika XHTML walked by `XhtmlBlockExtractor` (pages, heuristic headings, tables, code), not regex and not the flattened text output.
  - Plain text → `PlainTextParser`.
- **Parsing mode is configurable** through `rag.parsing.mode` (`auto` default, `tika` legacy extraction, `plain` passthrough), so the structured pipeline can be compared against the old behaviour. Text formats uploaded as raw bytes are decoded as UTF-8; binary formats keep their bytes for Tika.
- **Chunking layers on the existing algorithms.** `DocumentChunker` groups ordered blocks (`SectionAwareBlockGrouper`, `JsonBlockGrouper`) and hands each group to the variant's existing `TextSplitter` (`RecursiveCharacterChunker`, `TokenChunker`, `FixedSizeChunker`, `SemanticChunker`) inside `StructuralDocumentChunker`. `TextSplitter`/`ActiveTextSplitter` stay for compatibility; `DocumentChunker`/`ActiveDocumentChunker` resolve the active variant per call, exactly like the splitter.
- **Structure is preserved at chunk boundaries.** Tables and code blocks form their own protected groups when `preserveTables`/`preserveCode` hold; sections flush on heading or page changes when `respectSectionBoundaries` holds; JSON records group up to `maxRecordsPerChunk`. Chunk metadata receives `format`, `contentType`, `sectionPath`, `page`, `domPath`, `jsonPath`, `startOffset`, `endOffset`, `codeLanguage`, and `tableHeader`.
- **Provenance reaches the API.** Web crawling emits ordered `PageBlockDTO`s (`PageDTO.blocks`, additive; `text` is retained) and the ingestion side maps them through `PageDocumentMapper` without re-parsing text. `ChunkResult.metadata` exposes the chunk's provenance so query responses can cite page/section.
- **Layer boundaries are preserved.** Format libraries live only in `rag-common-ingestion`/`rag-webcrawler`; `rag-common-core` keeps the format-neutral model and ports, and parsers/chunkers stay free of embedding and vector-store concerns.

## Consequences

### Positive

- Chunks follow the document's real structure: sections stay together, tables and code stay intact, JSON records are not flattened.
- Chunks, query results, and citations can report where a passage came from (`page`, `sectionPath`, `domPath`, `jsonPath`, offsets).
- The established chunking strategies are reused instead of duplicated, so variant behaviour and per-variant storage (ADR-0015) are unchanged.
- The existing REST contracts, variant isolation, and table naming are preserved; `PageDTO.blocks` and `ChunkResult.metadata` are additive.

### Negative

- Every format now has a parser to maintain, and heuristic PDF headings/tables are best-effort: scanned PDFs still need OCR, which is out of scope.
- Ingesting a format with no registered parser falls back to plain text; a genuinely broken document still fails ingestion rather than silently degrading.
- Structure-aware chunking emits at least one chunk per protected block, so a document with many small tables/code blocks can produce more chunks than a pure character window.
- `rag-common-ingestion` gains jsoup, Jackson, commonmark, and Tika XHTML as direct dependencies, which the dependency analysis requires to stay declared.
