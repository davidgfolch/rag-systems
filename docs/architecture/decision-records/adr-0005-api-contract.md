# ADR-0005: API-First Contract via OpenAPI Code Generation

- **Status**: Accepted
- **Date**: 2026-09-01

## Context

The thin RAG TUI and the rag-* backend modules (rag-basic, rag-advanced, rag-agentic) plus the rag-memory and rag-webcrawler tools must interoperate over HTTP/WebSocket. Hand-maintained DTOs in each module drift apart and duplicate mapping code. We need one authoritative contract so any module can be switched in/out without breaking the TUI.

## Decision

Use an **API-first approach**: a dedicated `rag-contract` Maven module holds the canonical OpenAPI 3 specification (`rag-api.yaml`) and generates the shared model DTOs via `openapi-generator-maven-plugin`.

- The spec is the single source of truth in `apps/rag-contract/src/main/resources/openapi/rag-api.yaml`.
- Generated types land under `com.rag.contract.model` and are consumed by every module through a `rag-contract` dependency.
- The TUI generates HTTP/WebSocket clients from the same spec; rag-* modules implement the server side against the same DTOs.
- Server/client stubs are generated (`spring` generator); each module supplies its own controllers/handlers implementing the interface.

## Consequences

### Positive
- Modules are interchangeable: the TUI only knows the contract, never a concrete module
- Contract drift is impossible - generated types are shared
- A new rag-* module only needs to implement the contract to be switchable from the TUI

### Negative
- Code generation adds a build step (requires `openapi-generator-maven-plugin`)
- Spec changes require regenerating and rebuilding dependent modules

## Related

- [ADR-0007: Thin TUI + Module Control Plane](adr-0007-tui-interface.md)