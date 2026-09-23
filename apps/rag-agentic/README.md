# rag-agentic

Agentic RAG: retrieval augmented with an AI agent that decomposes the question into sub-queries, plans which tools to call, executes them, reflects on whether the gathered context is sufficient, and only then composes the final answer. Port **8083** (`RAG_AGENTIC_URL`).

## Architecture

Prompt-based JSON tool calling (no provider-specific tool-calling APIs, see ADR-0010): the agent loop asks the chat model for a JSON array of tool calls, parses it with Jackson, and falls back to a deterministic search over the planned sub-queries whenever the model yields nothing usable. This keeps tool selection cheap and portable across providers.

```
com.rag.agentic
├── domain/         - ToolCall, ToolResult, AgentStep, AgentTrace, constants
├── agents/         - QueryPlanner (deterministic decomposition),
│                     ToolCallParser (prompt-based JSON), ReflectionAgent
├── tools/          - SearchTool, DocumentSearchTool, WebSearchTool
├── orchestration/  - ToolRegistry, AgentLoop (bounded, self-reflecting),
│                     AgenticChatService (one-shot + streaming answers)
├── services/       - WebCrawlerClient (rag-webcrawler), AgenticRetrievalService
├── config/         - Spring wiring (schema rag_agentic, agent knobs)
└── api/            - AgenticQueryController, IngestionController,
                      chat/ChatWebSocketHandler (streaming, cancellable)
```

The loop (max steps `rag.agent.max-steps`, default 3) works as follows:

1. **Plan** — `QueryPlanner` splits compound questions (` and `, ` then `, `, `, `?`, `.`) into sub-queries.
2. **Act** — the model selects up to `maxSteps` tool calls; unknown/hallucinated tool names resolve to the search fallback.
3. **Reflect** — `ReflectionAgent` continues while no context has been gathered and the step budget remains.
4. **Answer** — a final prompt grounds the answer strictly on the gathered context; `askStream` streams those tokens so the TUI can cancel.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/query` | Shared contract retrieval (runs the loop in collect mode) |
| `POST` | `/api/query/agentic` | Full agent run: answer + step trace + sources |
| `GET`  | `/api/documents` | List ingested documents |
| `DELETE` | `/api/documents/{id}` | Delete a document |
| `POST` | `/api/documents/ingest` | Ingest raw content / raw bytes |
| `POST` | `/api/documents/ingest-file` | Ingest a multipart file (sync) |
| `POST` | `/api/documents/ingest-file-async` | Ingest a multipart file (async job) |
| `GET`  | `/api/documents/ingest-status/{id}` | Async ingestion status |
| `POST` | `/api/documents/ingest-url` | Fetch + ingest a URL via rag-webcrawler |
| `WS`   | `/ws/chat` | `ask`/`cancel` in, `token`/`done`/`error` out |

## Configuration

- Schema `rag_agentic` (via `PGVECTOR_SCHEMA`; blank default derives `rag_<dim>` from the active embedding dimension)
- `rag.agent.max-steps` (default `3`) — upper bound on tool calls / steps
- `rag.agent.tool-top-k` (default `4`) — default search widening per tool call
- Providers (chat/embedding) consumed remotely from rag-provider (`rag.provider.url`, default `http://localhost:8086`)
- Web fetches via rag-webcrawler (`rag.webcrawler.url`, default `http://localhost:8085`)

## Running

```bash
.\scripts\run.bat rag-agentic --profile local        # http://localhost:8083
```

**Note**: Agentic RAG benefits most from a decent model. For local, an 8GB GPU (qwen3:8b) is recommended over pure CPU.

## Related

- [rag-basic](../rag-basic/README.md) — the fixed-chunking baseline
- [rag-advanced](../rag-advanced/README.md) — reranking, hybrid search, metadata filtering
- [README module table](../../README.md#modules--strategy) — how rag-* modules are evaluated side by side