# rag-webcrawler

Intelligent **web fetching tool** — a runnable Spring Boot app (port **8085**, env `RAG_WEBCRAWLER_URL`) that fetches web pages, extracts text, and optionally picks the most relevant outbound links for a question. Used by the `add-url` flow of every rag-`*` module (which call it over HTTP), and by the TUI.

## Purpose

Give RAG ingestion a web source without embedding a crawler in every module: one service pages through (or links to) URLs and returns clean `PageDTO` payloads (title + text + links) that ingestion can chunk.

## How to run

```bash
# Windows
.\scripts\run.bat rag-webcrawler --profile local

# Linux/Mac
./scripts/run.sh rag-webcrawler --profile local
```

It is a plain HTTP service (no PostgreSQL, no provider dependency) — see [ADR-0009](../../docs/architecture/decision-records/adr-0009-rag-webcrawler.md).

## Quick access

| Endpoint | URL |
|----------|-----|
| Base | <http://localhost:8085> |
| Health | <http://localhost:8085/actuator/health> |
| Metrics | <http://localhost:8085/actuator/prometheus> |

## HTTP API

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/fetch` | Fetch a single page: `{"url": "https://..."}` → `PageDTO` (title, text, links) |
| `POST` | `/api/fetch/links` | Fetch the top-K pages most relevant to a question: `{"url": "...", "question": "..."}` → `List<PageDTO>` |

Example:

```bash
curl -X POST http://localhost:8085/api/fetch \
  -H "Content-Type: application/json" \
  -d '{"url":"https://spring.io/projects/spring-ai"}'
```

## Contents

```
com.rag.webcrawler
├── services/
│   ├── WebCrawlService           - orchestrates fetching + link prioritization
│   ├── fetching/
│   │   ├── WebPageFetcher        - port
│   │   └── JsoupWebPageFetcher   - jsoup HTTP implementation
│   └── ranking/
│       ├── LinkPrioritizer       - port
│       ├── DeterministicLinkPrioritizer - keyword/position scoring (default)
│       └── LlmLinkPrioritizer    - LLM-assisted relevance ranking
├── config/      - WebcrawlerConfig (wires prioritizer, fetcher)
└── api/         - FetchController
```

The link prioritizer is swappable via `CRAWLER_PRIORITIZER` (`deterministic` default, `llm` opt-in). `POST /api/fetch/links` uses it to crawl only the links most likely to answer the question — the basis of `add-url … "question"` in the TUI and of web-Q&A retrieval.

## Configuration

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `server.port` | `RAG_WEBCRAWLER_PORT` | `8085` | HTTP port (must match the port in `RAG_WEBCRAWLER_URL`) |
| `rag.crawler.prioritizer` | `CRAWLER_PRIORITIZER` | `deterministic` | Link ranking strategy (`deterministic`/`llm`) |

## Testing

```bash
.\scripts\test.bat rag-webcrawler
.\scripts\test.bat rag-webcrawler --coverage
```

## Related

- [ADR-0009: rag-webcrawler module](../../docs/architecture/decision-records/adr-0009-rag-webcrawler.md)
- [TUI ingestion guide](../../docs/guides/tui-ingestion.md) — how `add-url` uses this service
- [rag-contract](../rag-contract/README.md) — `PageDTO`, `FetchRequest`, `FetchLinksRequest` definitions