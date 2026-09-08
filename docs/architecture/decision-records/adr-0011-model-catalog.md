# ADR-0011: Refreshable Model Catalog (Models.dev)

- **Status**: Accepted
- **Date**: 2026-09-08

## Context

The TUI `connect` flow needs a browsable, up-to-date list of available models per
provider to pick a chat/embedding model at runtime. Today the rag-provider only
knows the env-seeded default specs ([ADR-0010](adr-0010-rag-provider.md)) and a
hand-typed model name on `POST /api/provider/chat|embedding`; there is no way for
a client to discover what a provider can serve before switching. Seeding this
data from config files would be stale on arrival, so the catalog should come
from an external, community-maintained source.

## Decision

Add a refreshable model catalog to **rag-provider**, sourced from
**Models.dev** (`https://models.dev/api.json` — an open database of AI model
metadata, pricing, and capabilities):

- **Contract** (`rag-contract`, `com.rag.contract.provider`): new transfer beans
  `ProviderModelDTO` (provider/model id, name, `ModelLimitsDTO`, `ModelCostDTO`,
  `ModelCapabilitiesDTO`, status) and `ModelCatalogDTO` (flat entry list, source,
  `fetchedAt`).
- **Adoption**: `ModelCatalogPort` (domain) → `ModelsDevCatalogClient`
  (adapter) fetches `api.json` and parses it defensively (missing sections are
  defaulted, upstream schema drift degrades gracefully instead of failing).
- **Service**: `ModelCatalogService` caches the catalog in memory; data is
  fetched lazily on first read and refreshed when older than a TTL
  (`rag.provider.catalog.ttl`, default 24h) or via an explicit refresh. A failed
  refresh keeps the previous snapshot and only logs a warning — the catalog is
  never a boot-time or runtime dependency (offline-safe).
- **API**: `GET /api/provider/catalog` (cached) and
  `POST /api/provider/catalog/refresh` (manual refresh), consumed later by the
  TUI `connect` flow.
- **Advisory switching**: `switchChat`/`switchEmbedding` log a warning when the
  catalog is reachable and does not list the requested model, but always allow
  the switch. Local-only models (e.g. Ollama's) are not in Models.dev and must
  not be rejected; an empty/unavailable catalog means "unverifiable", not
  "denied".
- **Configuration**: `rag.provider.catalog.{url,ttl,enabled}` (env: `MODEL_CATALOG_URL`,
  `MODEL_CATALOG_TTL`, `MODEL_CATALOG_ENABLED`), with the catalog disabled the
  service returns an empty catalog and never contacts the source.

## Consequences

### Positive
- TUI `connect` gets a real, refreshable model list per provider without config churn
- Nudge towards accurate model selection: limits, capabilities, and rough pricing
  are available before switching
- Offline-safe: no catalog, no problem — switching keeps working with advisory
  warnings only
- Single external source of truth; refresh is TTL-based or manual (no scheduled job)

### Negative
- Depends on a community-maintained external catalog (may be stale or briefly down; mitigated by caching + advisory-only behavior)
- Payload is several MB; parsed fully into memory on refresh (acceptable for a single-node learning deployment)
- Only Models.dev's metadata is used; live provider availability is still validated by the provider at first use
- Local-only models never appear in the catalog, so the TUI list is advisory for them

## Related

- [ADR-0010: Dedicated rag-provider Service for Model Switching](adr-0010-rag-provider.md)
- [ADR-0002: Provider Abstraction for Embeddings and LLMs](adr-0002-provider-abstraction.md)