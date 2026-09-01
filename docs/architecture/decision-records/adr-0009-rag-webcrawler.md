# ADR-0009: rag-webcrawler as an Intelligent Web Tool

- **Status**: Accepted
- **Date**: 2026-09-01

## Context

`add-url` requires fetching web pages. The TUI is thin and must not fetch. A plain HTTP GET deflected to jsoup is dumb: it cannot decide which outbound links are worth loading, nor answer "fetch me context about X from this website". We need an intelligent, reusable web-fetch tool with its own implementation and the option of AI/LLM-driven link selection.

## Decision

Add a dedicated **`rag-webcrawler`** Spring Boot module (tool only - no RAG pipeline):

- `POST /api/fetch {url}` → `Page` (url, title, text, links) via jsoup
- `POST /api/fetch/links {url, question?}` → fetched pages for the most relevant outbound links
- `LinkPrioritizer` strategy: an **LLM-driven** ranker (via the shared `ChatModel` contract) scores candidate links against an optional user question; a deterministic fallback (domain score, text density) is used when no LLM is configured
- Consumes the shared `rag-contract` `Page`/`FetchRequest`/`FetchLinksRequest` types

Orchestration note: the active rag-module calls `rag-webcrawler` during `ingest-url` (see [ADR-0007](adr-0007-tui-interface.md)). The TUI never talks to `rag-webcrawler` directly.

## Consequences

### Positive
- Intelligent crawl: fetches only relevant links, guided by question/context
- Reusable tool for any rag-* module and future features (site ingestion, link graphs)
- TUI stays thin; fetching logic encapsulated and testable
- Works without an LLM via the fallback ranker

### Negative
- LLM-driven link ranking adds latency and cost per crawl
- One more module/process to operate

## Related

- [ADR-0007: Thin TUI + Module Control Plane](adr-0007-tui-interface.md)