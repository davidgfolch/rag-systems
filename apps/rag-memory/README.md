# rag-memory

Conversation **history** service — a runnable Spring Boot app (port **8084**, env `RAG_MEMORY_URL`) that stores chat turns per conversation in PostgreSQL. Distinct from the vector stores: this keeps the *conversation* state, not document chunks.

## Purpose

Persist multi-turn chat context so the TUI (and any module) can look back at what was asked/answered. It complements the RAG vector stores (which hold document knowledge) without mixing concerns — see [ADR-0008](../../docs/architecture/decision-records/adr-0008-rag-memory.md).

## How to run

Requires PostgreSQL (`.\scripts\docker.bat up`) and, for `local` profile defaults, nothing else — memory is a pure data service.

```bash
# Windows
.\scripts\run.bat rag-memory --profile local

# Linux/Mac
./scripts/run.sh rag-memory --profile local
```

## Quick access

| Endpoint | URL |
|----------|-----|
| Base | <http://localhost:8084> |
| Health | <http://localhost:8084/actuator/health> |
| Metrics | <http://localhost:8084/actuator/prometheus> |

## HTTP API

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/conversations` | List conversations |
| `POST` | `/api/conversations?title=...` | Create a conversation |
| `GET` | `/api/conversations/{id}/messages` | List messages in a conversation |
| `POST` | `/api/conversations/{id}/messages` | Append a message |

Example:

```bash
curl -X POST "http://localhost:8084/api/conversations?title=My%20Chat"
curl -H "Content-Type: application/json" \
  -d '{"role":"user","content":"What is RAG?"}' \
  http://localhost:8084/api/conversations/{id}/messages
curl http://localhost:8084/api/conversations/{id}/messages
```

## Contents

```
com.rag.memory
├── domain/        - ConversationEntity, ChatMessageEntity (JPA, schema rag_memory)
├── repositories/  - ConversationRepository, MessageRepository (Spring Data JPA)
├── services/      - ConversationService (business rules)
└── api/           - ConversationController, ApiExceptionHandler
```

Data lives in its own PostgreSQL schema `rag_memory` (created on boot — never collides with the vector-store schemas). `ddl-auto: update` manages the tables.

## Configuration

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `server.port` | `RAG_MEMORY_PORT` | `8084` | HTTP port (must match the port in `RAG_MEMORY_URL`) |
| `spring.datasource.*` | `PGVECTOR_HOST/PORT/DB/USER/PASSWORD` | `localhost:5432/rag` | Postgres connection |

## Testing

```bash
.\scripts\test.bat rag-memory
.\scripts\test.bat rag-memory --coverage
```

## Related

- [ADR-0008: rag-memory module](../../docs/architecture/decision-records/adr-0008-rag-memory.md)
- [rag-contract](../rag-contract/README.md) — `ConversationDTO`, `ChatMessageDTO` definitions
- [README module table](../../README.md#modules--strategy)