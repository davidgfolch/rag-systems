# TUI Ingestion & Chat Guide

The `rag-tui` module is an interactive terminal for adding content to a RAG system
(local files and web pages) and then asking grounded questions against it. This guide
explains the flow, why it is structured this way, and how to extend it.

## What it does

```
add-file <path>  ─┐
                  ├──► Document ──► parse ──► split ──► embed ──► store
add-url <url>   ──┘                    │        │        │         │
                                 Tika / plain  recursive  adapter  in-memory/PgVector

ask <question> ──► retrieve (vector store, topK) ──► build prompt ──► chat model ──► answer + sources
```

Two independent capabilities, both reusing the `rag-common` strategy interfaces:

1. **Ingestion** — turn files/web pages into embedded chunks.
2. **Chat (RAG QA)** — retrieve the most relevant chunks and generate a grounded answer.

## Why separate `ui` from `services`?

The `CommandDispatcher` is pure logic: it parses a command string and returns a
`CommandResult` (a message + whether to exit). It never touches the terminal. The
`InteractiveShell` is the only class that touches `Reader`/`Writer`. This separation
makes the whole command surface unit-testable without mocking `System.in`/`System.out` —
you pass a `StringReader`/`StringWriter` and assert on the output.

Follow the same pattern when adding commands: put validation and orchestration in the
dispatcher/services, keep I/O in the shell, and keep every file under 200 lines.

## Adding a new source (e.g. a Google Doc or a raw paste)

1. Add a strategy interface in `rag-common` if the source crosses modules (like
   `WebPageFetcher`), or keep it local to `rag-tui` if it is TUI-specific.
2. Implement it behind the interface (provider abstraction), and inject it into
   `CommandDispatcher` via `RagTuiConfig`.
3. Add the command handler in the dispatcher that builds a `Document` (with source
   metadata) and calls `ingestionService.ingest(...)`.
4. Write a unit test for the dispatcher and the new fetcher (mock the network),
   then run `.\scripts\test.bat rag-tui --coverage`.

## Provider abstraction & profiles

No concrete model/store is hardcoded. The `local` profile uses Ollama
(`nomic-embed-text` embeddings, `qwen3:4b` chat); the `cloud` profile uses OpenAI.
The vector store defaults to in-memory (no Postgres needed) and can be switched to
PgVector with `rag.vector-store.type=pgvector`.

```bash
.\scripts\run.bat --profile local
VECTOR_STORE_TYPE=pgvector .\scripts\run.bat --profile local
```

## Testing

- Unit tests mock `DocumentParser`, `TextSplitter`, `EmbeddingModel`, `VectorStore`,
  and `ChatModel` — never call real providers.
- `JsoupWebPageFetcherTest` injects a mock jsoup `Connection` (no network).
- `InteractiveShellTest` drives a `StringReader` and checks `StringWriter` output.
- `ArchitectureTest` enforces layer rules and interface implementations.
- Coverage gate is ≥ 85%, enforced by the `verify` goal.
