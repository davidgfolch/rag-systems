# ADR-0012: TUI `connect` Command

- **Status**: Accepted
- **Date**: 2026-09-08

## Context

The TUI is the control plane for the rag-* modules, but its active module's
models are chosen at boot from env config only — there is no way to discover
what models a provider can serve or to switch chat/embedding models at runtime
from the terminal. The rag-provider service now owns all model/provider clients
and exposes a refreshable models.dev catalog
([ADR-0010](adr-0010-rag-provider.md), [ADR-0011](adr-0011-model-catalog.md)).
The TUI needs a `connect` command to use that surface from the shell.

## Decision

Add a `connect` command family to the TUI (`CommandDispatcher` +
`CommandRegistry`), backed by a new `ProviderClient` (REST client for the
rag-provider companion service):

- `connect` — provider status (active chat/embedding specs, embedding
  dimension) plus catalog freshness (source, last fetch, model count).
- `connect catalog [<provider>]` — browse the catalog, grouped by provider,
  optionally filtered by provider id prefix; each model line shows id, name,
  context/output limits, cost, and capabilities (reasoning/tool-call/
  structured-output).
- `connect chat <provider> <model>` / `connect embedding <provider> <model>` —
  switch the chat or embedding model via `POST /api/provider/chat|embedding`.
  Switching is advisory (the provider logs a warning when a model is not in the
  catalog but always applies it), mirroring local-only models such as Ollama's.
- `connect refresh` — force a catalog refresh via
  `POST /api/provider/catalog/refresh`.

Supporting decisions:

- **Rendering** lives in a dedicated `ConnectCommand` (ui layer), not inline in
  `CommandDispatcher`, to keep the dispatcher's file length within the
  200-line guideline. The dispatcher only routes `connect` to it.
- **rag-provider joins the module registry** as a 4th module on
  `RAG_PROVIDER_URL` (default `http://localhost:8086`) so `modules`, `start`,
  and `stop` manage it like any other module, giving `connect` a listening
  service to talk to.
- **Document-less service**: rag-provider has no `/api/documents`, so
  `RagApiClient.listDocuments` treats a 404 as an empty list; `documents` and
  `delete` skip it naturally instead of surfacing an error.
- **Configuration**: the provider URL is a dedicated `rag.provider.url`
  property (env `RAG_PROVIDER_URL`), independent of the active rag-* module.

## Consequences

### Positive
- One-shot model discovery and switching from the terminal, completing the
  M1–M3 `connect` workflow (provider service → catalog → TUI)
- `connect chat/embedding` is advisory but never blocks local/no-catalog models
- rag-provider is now startable/observable like the other modules
- Command set stays small; dispatcher file stays under the size cap

### Negative
- `use rag-provider` would misroute chat/ask (rag-provider has no chat UI
  endpoints); acceptable as user error — the provider is meant to be the model
  backend, not the active chat module
- `connect catalog` list output is dense for providers with many models;
  filtering by provider prefix mitigates this

## Related

- [ADR-0010: Dedicated rag-provider Service for Model Switching](adr-0010-rag-provider.md)
- [ADR-0011: Refreshable Model Catalog (Models.dev)](adr-0011-model-catalog.md)