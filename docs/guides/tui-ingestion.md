# TUI Ingestion & Chat Guide

The `rag-tui` module is an interactive terminal for adding content to a RAG system
(local files and web pages) and then asking grounded questions against it. This guide
explains the flow, why it is structured this way, and how to extend it.

## What it does

```
add-file <path>  ─┐
                  ├──► Document ──► parse ──► group ──► split ──► embed ──► store
add-url <url>   ──┘   (crawl: root + ranked links)   │        │        │         │
                          format-aware parser   sections  strategy  adapter  in-memory/PgVector

ask <question> ──► retrieve (vector store, topK) ──► build prompt ──► chat model ──► answer + sources
```

Two independent capabilities, both reusing the `rag-common-*` strategy interfaces:

1. **Ingestion** — turn files/web pages into embedded chunks.
2. **Chat (RAG QA)** — retrieve the most relevant chunks and generate a grounded answer.

## Getting help (`help [<command>]`)

`help` prints every command grouped by concept. Pass a command name to get its
arguments, defaults and behaviour notes:

```
> help
Available commands:

Modules
  modules                list rag-* modules and their state
  use <module>           switch the active module
  ...

> help variant
variant - list, create or activate pipeline variants

Usage:
  variant                        list variants and pick one to activate
  variant <variant-id>           activate a variant by id
  variant add <strategy> [<size>] [<overlap>]
                                 create one; missing values are prompted

Recommended values:
  RECURSIVE 512/128   prose and mixed docs - start here
  TOKEN     256/64    strict token windows (size/4 must exceed overlap/4)
  ...

> help connect chat
connect - browse models or switch chat/embedding
...
```

`help variant` is the reference for chunking: it explains what `<size>` and
`<overlap>` mean per strategy, the `0 <= overlap < size` rule (plus
`size/4 > overlap/4` for `TOKEN`), and recommended values by content type. A
connect subcommand such as `help connect chat` resolves to the `connect` page.
Tab completion covers command names after `help `, as well as module names,
providers, models and `variant add` strategies.

## Crawling a site (`add-url`)

`add-url` does not ingest a single page: it runs an interactive breadth-first crawl that the
active rag-module orchestrates.

- The module fetches the root page through **rag-webcrawler** and returns its URL and title to the TUI.
- In interactive mode the TUI asks **one question per crawl** (not per page). That question is sent
  with every page and ranks its outbound links; a blank answer uses the deterministic ranker.
- The initial crawl set is the root plus up to **5** links ranked from the root.
- After the initial set, the TUI asks **“Go on?” once** if more links are queued — but only when the
  user did not already set a page cap. `--max-pages N` and `--all` skip that question.
- `--all` crawls every discovered page **without any prompt**: no ranking question, no “Go on?”,
  and already-ingested pages are skipped silently.
- Each page is submitted for **asynchronous** ingestion, so the shell stays responsive and reports
  each page as its job completes. Submission is bounded by `rag.ingestion.max-concurrent`
  (default **2**); if the queue wait is exceeded the job fails with an explicit capacity error
  instead of overloading the provider. See the [Resilience guide](resilience.md).
- Type `add-url-cancel` (or `cancel`) at any crawl prompt, or run the `add-url-cancel` command, to
  stop scheduling new pages. Pages already submitted finish ingesting.
- The frontier is de-duplicated per crawl and skips pages whose `source` is already ingested on the
  module. Both `maxPages` (default **50**) and `sameHostOnly` (default **true**) can be set on
  `POST /api/documents/crawl`.

The legacy `POST /api/documents/ingest-url` endpoint remains as a single-page API.

## De-duplication

Re-ingesting the same content is detected before submission:

- **`add-file` / `add-folder`** — a SHA-256 content hash is computed from the file bytes and sent as
  `contentHash` metadata. The TUI lists documents on reachable rag-* modules and skips the file when a
  document already carries the same hash. `add-file` asks `Override? (y/N)`; `add-folder` skips
  duplicates silently.
- **`add-url`** — every page (root and sublinks) is checked against the document `source` metadata by
  **domain + URI** (host + path; `http`/`https`, `#fragment` and trailing-slash differences don't
  count as different pages). In interactive mode each existing page asks:
  `Enter = override`, `Esc = ignore`, `y = override the rest`, `n = ignore the rest`.
  `--all` skips existing pages silently; `--override-all` overrides every existing page without
  asking.
- **Override = delete + re-ingest**: confirming `y`/Enter deletes the existing document (removing its
  chunks from PgVector) before submitting the new content, so no stale chunks remain. The module is
  told to ingest the page via the `override` flag on `POST /api/documents/crawl/{id}/step`.

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
