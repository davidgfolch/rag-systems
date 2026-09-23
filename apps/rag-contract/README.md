# rag-contract

The **API-first contract** of the monorepo: the single OpenAPI specification plus all generated/supported DTOs, shared constants, and WebSocket frame types. A **library** — no runnable app, no HTTP port. Every runnable module depends on it.

## Purpose

Define every REST endpoint, payload, and WebSocket frame **once** so all `rag-*` modules expose the exact same API shape. Because the contract is shared, the TUI and the modules can be developed and versioned independently but never drift apart.

## Contents

```
com.rag.contract
├── constants/
│   ├── ApiPaths     - shared endpoint path constants (/api/documents, /api/query, /api/provider, ...)
│   └── FrameTypes   - WebSocket frame type constants
├── ws/              - ChatRequest, ChatResponse (WebSocket frames)
├── provider/        - ProviderStatusDTO, ProviderModelDTO, ModelCatalogDTO, ModelLimitsDTO,
│                      ModelCostDTO, ModelCapabilitiesDTO, ModelSpecDTO, EmbedRequest/Response,
│                      CompleteRequest/Response, ConfigureProviderRequest, ...
└── resources/openapi/
    └── rag-api.yaml - the single OpenAPI 3.0.3 specification
```

## The OpenAPI spec

[`rag-api.yaml`](src/main/resources/openapi/rag-api.yaml) is the source of truth for the HTTP API. It covers the ingestion, query, provider, webcrawler, and memory endpoint groups.

There is **no bundled Swagger UI** in any module — treat the spec as your API reference:

- Read it directly: <https://github.com/davidgfolch/rag-systems/blob/main/apps/rag-contract/src/main/resources/openapi/rag-api.yaml>
- Or paste it into the [Swagger editor](https://editor.swagger.io) to browse an interactive UI.

## Naming rules

Because these DTOs cross the wire, the contract enforces the naming convention that prevents layer collisions:

- **DTOs / transfer types** end with `DTO`, `Request`, `Response`, or `Result` (`ConversationDTO`, `QueryRequest`, `PageDTO`, `IngestJobResponse`).
- Domain beans (per rag-common-core) stay plain (`Chunk`, `Document`).

Enforced by `DtoNamingTest` in this module.

## Why API-first?

A shared, versioned contract lets every module be consumed identically by the TUI control plane and by external clients, and keeps the runnable apps thin (they only implement the spec). See [ADR-0005: API-First Contract](../../docs/architecture/decision-records/adr-0005-api-contract.md).

## Testing

```bash
.\scripts\test.bat rag-contract
```

## Related

- [Root README API reference](../../README.md#quick-access-urls)
- [ADR-0005: API-First Contract](../../docs/architecture/decision-records/adr-0005-api-contract.md)
- [rag-common-core](../rag-common-core/README.md) — the domain beans these DTOs map to