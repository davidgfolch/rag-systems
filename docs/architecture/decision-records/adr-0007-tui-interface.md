# ADR-0007: Thin TUI + Module Control Plane

- **Status**: Accepted
- **Date**: 2026-09-01

## Context

The TUI must let a user switch between rag-* implementations at runtime. Each rag-* module is an independent Spring Boot application with its own schema, chunking, and retrieval approach. Dynamic bean replacement inside one JVM was rejected as complex and fragile. The TUI must remain only an interface, not re-implement RAG logic.

## Decision

Make the TUI a **thin interface + control plane**:

- The TUI contains no RAG logic (no chunking, parsing, embedding, vectors, or chat services)
- It **starts/stops rag-* modules as child processes** (`ProcessBuilder`) and discovers health via `GET /api/health`/Actuator
- It routes all work to the **currently active module** over HTTP/WebSocket using the shared contract ([ADR-0005](adr-0005-api-contract.md))
- Module ports/URLs are configured in the main `.env` as an explicit map (`RAG_BASIC_URL`, `RAG_ADVANCED_URL`, `RAG_AGENTIC_URL`, `RAG_MEMORY_URL`, `RAG_WEBCRAWLER_URL`)

**Chat streaming** uses a **WebSocket** (`/ws/chat`) because the user must be able to cancel an in-flight streamed answer: WebSocket natively supports an outbound `cancel` message and socket close, which unidirectional SSE cannot do cleanly.

**Crawl-then-ingest** is orchestrated by the active rag-module, not the TUI:

```
TUI --ingest-url--> active rag-module --fetch--> rag-webcrawler --Page--> active rag-module
active rag-module: chunk (its own chunker) -> embed -> store -> respond to TUI
```

## Consequences

### Positive
- Runtime module switching without dynamic bean replacement
- Modules stay independently runnable and comparable
- TUI stays small, testable, and provider-agnostic
- Cancelable streaming via WebSocket

### Negative
- TUI needs process management and lifecycle handling (start/stop/health)
- WebSocket adds a more complex transport than plain REST/SSE
- Each module must expose the shared contract to be usable from the TUI

## Related

- [ADR-0005: API-First Contract](adr-0005-api-contract.md)
- [ADR-0006: Data Store Isolation](adr-0006-data-store-isolation.md)
- [ADR-0009: rag-webcrawler Module](adr-0009-rag-webcrawler.md)