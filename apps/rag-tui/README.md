# rag-tui

Terminal user interface for **adding documents and web pages** to a RAG system and **chatting** with the ingested content — no web UI required. It is a **non-web app** (`web-application-type: none`): it exposes no HTTP server of its own but orchestrates the other modules over HTTP and WebSocket.

## Purpose

rag-basic and friends expose REST APIs; rag-tui gives you a lightweight, interactive terminal for the ingestion + question-answering flow. Great for learning, quick experimentation, and scripting against a local RAG corpus without building a frontend. It is a **control plane**: `start`/`stop`/`use` manage the `rag-*` modules, and it routes chat to the active one over WebSocket (`/ws/chat`).

## Dependencies

rag-tui talks to other services at runtime — they must be reachable:

| Service                        | Env var / default                            | Used for                                 |
|--------------------------------|----------------------------------------------|------------------------------------------|
| rag-provider                   | `RAG_PROVIDER_URL` (`http://localhost:8086`) | `connect` (model switching), status      |
| rag-basic / rag-advanced / ... | `RAG_BASIC_URL` (`8081`) ...                 | `add-file`/`add-url`/`ask`/`start`/`use` |
| rag-memory                     | `RAG_MEMORY_URL` (`8084`)                    | conversation history                     |
| rag-webcrawler                 | `RAG_WEBCRAWLER_URL` (`8085`)                | `add-url` (module-orchestrated)          |

## Features

| Feature                     | Description                                                                                                                                                                                                 |
|-----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `add-file <path>`           | Ingest a local document (PDF, DOCX, TXT, HTML) via Tika; de-duplicated by SHA-256 content hash                                                                                                              |
| `add-url <url>`             | Crawl a site from the URL: the active module fetches and ranks pages, the TUI asks one ranking question per crawl, and results are de-duplicated by domain + URI (`--all`, `--max-pages`, `--override-all`) |
| `add-url-cancel`            | Stop the crawl currently being driven (also accepted at crawl prompts)                                                                                                                                      |
| `ask <question>`            | Retrieval-augmented answer with cited source chunks                                                                                                                                                         |
| `connect`                   | Provider status; browse model catalog; switch chat/embedding provider+model at runtime (Ollama default, OpenRouter/OpenAI-compatible registerable)                                                          |
| `variant`                   | List, create and switch the active module's pipeline variants; each variant owns its `variant_<id>` table in the module schema                                                                              |
| `help [<command>]` / `quit` | Grouped usage, per-command detail (arguments, recommended values) and exit                                                                                                                                  |

## Architecture

Layered per the monorepo guidelines, depending only on the `rag-common-core` strategy interfaces:

```
ui ──────────► services ──────────► rag-common-core (interfaces)
 │               │   │
 │               │   └─► fetching (WebPageFetcher)      ui ──► fetching
 │               └─► adapter (Chat/Embedding)           config wires everything
 └─► (terminal loop)
```

- `ui` – `CommandDispatcher` (pure command routing) + `InteractiveShell` (I/O loop)
- `services` – `IngestionService` (parse→split→embed→store), `ChatService` (retrieve→generate), `FileDocumentLoader`
- `fetching` – `WebPageFetcher` + `JsoupWebPageFetcher`
- `adapter` – `SpringAiChatModel`, `SpringAiEmbeddingModel` (provider abstraction)
- `chunking` / `parsing` / `vectorstore` – swappable strategies

The TUI uses the provider abstraction: run with the `local` profile (Ollama) or `cloud` profile (OpenAI), and swap the vector store between in-memory and PgVector via properties.

## Running

```bash
# Windows (rag-tui is the default run target)
.\scripts\run.bat --profile local

# Linux/Mac
./scripts/run.sh --profile local
```

> rag-provider must be running first — start it with `.\scripts\run.bat rag-provider --profile local` (the TUI uses it for chat/embeddings and `connect`). For `add-file`/`ask` against a module, start one too (`start rag-basic` from within the TUI, or `.\scripts\run.bat rag-basic --profile local`).

Example session:

```
RAG TUI - type 'help' for commands, 'quit' to exit
> add-file docs/report.pdf
Ingested document file-<uuid> -> 12 chunks
> add-url https://spring.io/projects/spring-ai
Question to rank links for this crawl (blank = none, 'add-url-cancel' to stop):
Initial pages submitted. Go on and crawl more pages? (y/N) n
Crawl cancelled: 5 page(s) submitted for async ingestion, 0 page(s) skipped, 12 link(s) skipped. Discovery queued 0 page(s). I'll report each page and print a summary when all async jobs finish.
Ingestion of page https://spring.io/projects/spring-ai complete: 8 chunks.
...
Crawl ingestion finished: 5 page(s) complete, 0 failed, 42 chunk(s) total.
> ask what is spring ai
Answer:
Spring AI is a framework that simplifies building RAG applications...

Sources:
 - Spring AI simplifies building retrieval-augmented generation apps.
 - The framework handles embeddings, vector search and chat orchestration.
```

## Pipeline variants

`variant` lists the active module's variants and activates the one you pick. `variant add <strategy> [<size>] [<overlap>]` registers a new one, prompting for anything you omit and defaulting the window to the active variant's:

```
> variant add TOKEN 256 64
Variant created on rag-basic: v2 (TOKEN 256/64). Activate it with: variant v2
> variant v2
Variant active on rag-basic: TOKEN 256/64, nomic-embed-text 768d
```

The seeded `boot` variant keeps the module's `chunks` table; every other variant writes to `variant_<id>` in the module schema. `variant add` inherits the embedding model rag-provider is currently using, so each variant embeds with its own model and `connect embedding` only drives the `boot` variant. See [ADR-0015](../../docs/architecture/decision-records/adr-0015-pipeline-variants.md).

## Configuration

Set via environment variables or `application.yml`:

| Property                              | Default     | Description                                                                                            |
|---------------------------------------|-------------|--------------------------------------------------------------------------------------------------------|
| `rag.vector-store.type`               | `in-memory` | `in-memory` or `pgvector`                                                                              |
| `rag.parsing.mode`                    | `tika`      | `tika` or `plain`                                                                                      |
| `rag.chunking.size`                   | `512`       | Max chunk size (chars)                                                                                 |
| `rag.chunking.overlap`                | `64`        | Chunk overlap                                                                                          |
| `rag.chat.top-k`                      | `4`         | Retrieval depth for `ask`                                                                              |
| `rag.http.read-timeout-seconds`       | `60`        | Read timeout for ordinary module calls                                                                 |
| `rag.http.crawl-read-timeout-seconds` | `600`       | Read timeout for crawl start/step/cancel; must exceed `rag.ingestion.queue-wait` plus crawler timeouts |

Profiles: `local` (Ollama) and `cloud` (OpenAI). See the root `application.yml` and `docs/guides/tui-ingestion.md`.

## Testing

```bash
.\scripts\test.bat rag-tui
.\scripts\test.bat rag-tui --coverage   # enforces >= 85%
```

See the architecture rules in `ArchitectureTest`: `ui` may not reach into config/adapters/stores; `services` depend only on the `rag-common-core` strategy interfaces.
