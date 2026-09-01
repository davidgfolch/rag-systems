# ADR-0008: rag-memory Module for Conversation Memory

- **Status**: Accepted
- **Date**: 2026-09-01

## Context

The TUI is stateless by design ([ADR-0007](adr-0007-tui-interface.md)). Chat sessions span many questions, and users must be able to see history across module switches. Without a dedicated store, conversation state would leak into the TUI or be mixed with per-module vector data.

## Decision

Add a dedicated **`rag-memory`** Spring Boot module owning **chat/conversation memory only** (not vectors, not a document registry):

- Schema `rag_memory` in the shared PostgreSQL
- Tables: `conversations`, `messages`
- REST API (from the shared `rag-contract`): create/list conversations, list/append messages
- The TUI calls `rag-memory` to start conversations and render `history`; the active rag-module streams answers back and the message pair is persisted

Out of scope: this module does not store document metadata or vectors - that belongs to each rag-* module's own schema (per [ADR-0006](adr-0006-data-store-isolation.md)).

## Consequences

### Positive
- Conversation history survives module switches and TUI restarts
- TUI stays thin; memory is a separate independently-deployable service
- Clean separation: vector data in rag-* schemas, conversational state in rag_memory

### Negative
- One more module/process to run and monitor
- Two writes per Q&A (module streams answer + memory persists it) if persistence is TUI-driven

## Related

- [ADR-0006: Data Store Isolation](adr-0006-data-store-isolation.md)
- [ADR-0007: Thin TUI + Module Control Plane](adr-0007-tui-interface.md)