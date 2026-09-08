# ADR-0010: Dedicated rag-provider Service for Model Switching

- **Status**: Accepted
- **Date**: 2026-09-08

## Context

The TUI `connect` feature must let users switch chat/embedding models at runtime without restarting a RAG module. Today each rag-* module embeds Spring AI Ollama/OpenAI auto-configuration and selects a provider only via Spring profiles (`local`/`cloud`, see [ADR-0002](adr-0002-provider-abstraction.md)); swapping models at runtime is impossible, and enabling both OpenAI and Ollama starters in one module drags in conflicting auto-configurations.

## Decision

Add a dedicated **`rag-provider`** Spring Boot module that owns all provider/model switching and is consumed by other rag-* modules over HTTP:

- **Port 8086** (`RAG_PROVIDER_URL` default `http://localhost:8086`); rag-memory and rag-webcrawler keep 8084/8085.
- **API contract** lives in `rag-contract` under `com.rag.contract.provider`: `GET /api/provider` (status), `POST /api/provider/chat|embedding` (switch active model), `POST /api/provider/configure` (upsert a provider profile), plus compute endpoints `POST /api/complete`, `POST /api/embed`, and `POST /api/chat/stream` (SSE, reusing the `ChatResponse` token/done/error frame shape).
- **Self-contained module**: depends only on `rag-contract` + `rag-observability` + Spring AI starters, deliberately **not** on `rag-common` (avoid a dependency cycle; the module is plain WebMVC with `SseEmitter`, no reactive stack).
- **Domain layering**: `domain` (pure records: `ProviderType`, `ModelSpec`, `ProviderProfile`) ← `adapter` (`ProviderClientFactory` builds Ollama/OpenAI/OpenAI-compatible clients lazily — no network on boot) ← `services` (`ProviderRegistry` persists/upserts profiles, `ModelRouter` holds volatile active specs, `ChatService`, `EmbeddingService`) ← `api` (controllers, restful mapping of contract DTOs).
- **Lazy dimension resolution**: an active embedding model's vector dimension is probed on demand (`dimensions()` with an `embed("probe")` fallback), cached, and invalidated on `switchEmbedding`. The provider may run fully offline at boot.
- **Consumer side**: `rag-basic` drops its Ollama/OpenAI starters and talks to `rag-provider` through thin `rag-common` bridges (`ProviderHttpClient`, `RemoteChatModelPort`, `RemoteEmbeddingModel`), registered as the standard `ChatModelPort`/`EmbeddingModel` beans, selectable per profile.

## Consequences

### Positive
- Runtime model switching without restarting the RAG module or the provider
- Health check via `GET /api/provider` used by the TUI `connect` flow
- Multiple providers coexist (Ollama local + OpenAI cloud + any OpenAI-compatible endpoint)
- `rag-common` modules lose provider auto-configuration bloat and startup probing
- Provider module is small, testable in isolation, and enforces strict layering via ArchUnit

### Negative
- One more module/process to operate (port 8086)
- Each downstream query embeds a local HTTP round-trip to `rag-provider`
- Default active models are seeded from env with a single-model-per-role assumption (one chat + one embedding provider); the cross-provider default caveat is documented and refined in a later milestone (Models.dev catalog + refresh)

## Related

- [ADR-0002: Provider Abstraction for Embeddings and LLMs](adr-0002-provider-abstraction.md)
- [ADR-0005: Unified API Contract Module](adr-0005-api-contract.md)
- [ADR-0007: Thin TUI + Module Control Plane](adr-0007-tui-interface.md)