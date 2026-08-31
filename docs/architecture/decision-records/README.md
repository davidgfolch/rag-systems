# Architecture Decision Records

This directory records significant architecture decisions for the RAG Systems project.

## ADR Index

| ADR | Status | Title |
|-----|--------|-------|
| [ADR-0001](adr-0001-monorepo-layout.md) | Accepted | Monorepo layout with decoupled modules |
| [ADR-0002](adr-0002-provider-abstraction.md) | Accepted | Provider abstraction for embeddings/LLMs |
| [ADR-0003](adr-0003-local-first.md) | Accepted | Local-first development with Ollama |

## How to add an ADR

1. Copy `adr-template.md` to `adr-XXXX-short-title.md`
2. Assign the next number (increment from last)
3. Fill in the template
4. Add a row to the index table

## Statuses

- **Proposed** - Under discussion
- **Accepted** - Approved and implemented
- **Deprecated** - Replaced by a later decision
- **Superseded by [ADR-XXXX]** - Replaced
