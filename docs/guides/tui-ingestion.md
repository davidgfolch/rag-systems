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

Two independent capabilities, both reusing the `rag-common-*` strategy interfaces:

1. **Ingestion** — turn files/web pages into embedded chunks.
2. **Chat (RAG QA)** — retrieve the most relevant chunks and generate a grounded answer.

## De-duplication

Re-ingesting the same content is detected before submission, with an `Override? (y/N)` prompt:

- **`add-file` / `add-folder`** — a SHA-256 content hash is computed from the file bytes and sent as
  `contentHash` metadata. The TUI lists documents on reachable rag-* modules and skips the file when a
  document already carries the same hash.
- **`add-url`** — documents match on the `source` metadata by **domain + URI** (host + path;
  `http`/`https`, `#fragment` and trailing-slash differences don't count as different pages).
- **Override = delete + re-ingest**: confirming `y` deletes the existing document (removing its chunks
  from PgVector) before submitting the new content, so no stale chunks remain.

The matching only consults document metadata on reachable modules; a document without `contentHash` or
`source` metadata simply never matches, so nothing is blocked.

## Why separate `ui` from `services`?

The `CommandDispatcher` is pure logic: it parses a command string and returns a
`CommandResult` (a message + whether to exit). It never touches the terminal. The
`InteractiveShell` is the only class that touches `Reader`/`Writer`. This separation
makes the whole command surface unit-testable without mocking `System.in`/`System.out` —
you pass a `StringReader`/`StringWriter` and assert on the output.

Follow the same pattern when adding commands: put validation and orchestration in the
dispatcher/services, keep I/O in the shell, and keep every file under 200 lines.

## Adding a new source (e.g. a Google Doc or a raw paste)

1. Add a strategy interface in `rag-common-core` if the source crosses modules (like
   `WebPageFetcher`), or keep it local to `rag-tui` if it is TUI-specific.
2. Implement it behind the interface (provider abstraction), and inject it into
   `CommandDispatcher` via `RagTuiConfig`.
3. Add the command handler in the dispatcher that builds a `Document` (with source
   metadata) and calls `ingestionService.ingest(...)`.
4. Write a unit test for the dispatcher and the new fetcher (mock the network),
   then run `.\scripts\test.bat rag-tui --coverage`.

## Provider abstraction & profiles

No concrete model/store is hardcoded. The `local` profile uses Ollama
(`nomic-embed-text` embeddings, `phi4` chat); the `cloud` profile uses OpenAI.
The vector store defaults to in-memory (no Postgres needed) and can be switched to
PgVector with `rag.vector-store.type=pgvector`.

```bash
.\scripts\run.bat --profile local
VECTOR_STORE_TYPE=pgvector .\scripts\run.bat --profile local
```

## Testing

- Unit tests mock `DocumentParser`, `TextSplitter`, `EmbeddingModelPort`, `VectorStorePort`,
  and `ChatModelPort` — never call real providers.
- `JsoupWebPageFetcherTest` injects a mock jsoup `Connection` (no network).
- `InteractiveShellTest` drives a `StringReader` and checks `StringWriter` output.
- `ArchitectureTest` enforces layer rules and interface implementations.
- Coverage gate is ≥ 90%, enforced by the JaCoCo `check` rule on every test run (`.\scripts\test.bat`), for this module and all others with source code.
