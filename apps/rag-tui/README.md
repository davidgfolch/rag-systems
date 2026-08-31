# rag-tui

Terminal user interface for **adding documents and web pages** to a RAG system and **chatting** with the ingested content — no web UI required.

## Purpose

rag-basic and friends expose REST APIs; rag-tui gives you a lightweight, interactive terminal for the ingestion + question-answering flow. Great for learning, quick experimentation, and scripting against a local RAG corpus without building a frontend.

## Features

| Feature | Description |
|---------|-------------|
| `add-file <path>` | Ingest a local document (PDF, DOCX, TXT, HTML) via Tika |
| `add-url <url>` | Fetch and ingest a web page (jsoup) |
| `ask <question>` | Retrieval-augmented answer with cited source chunks |
| `help` / `quit` | Usage and exit |

## Architecture

Layered per the monorepo guidelines, depending only on the `rag-common` strategy interfaces:

```
ui ──────────► services ──────────► rag-common (interfaces)
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
# Windows
.\scripts\run.bat rag-tui --profile local

# Linux/Mac
./scripts/run.sh rag-tui --profile local
```

Example session:

```
RAG TUI - type 'help' for commands, 'quit' to exit
> add-file docs/report.pdf
Ingested document file-<uuid> -> 12 chunks
> add-url https://spring.io/projects/spring-ai
Ingested web page https://spring.io/projects/spring-ai -> 8 chunks
> ask what is spring ai
Answer:
Spring AI is a framework that simplifies building RAG applications...

Sources:
 - Spring AI simplifies building retrieval-augmented generation apps.
 - The framework handles embeddings, vector search and chat orchestration.
```

## Configuration

Set via environment variables or `application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `rag.vector-store.type` | `in-memory` | `in-memory` or `pgvector` |
| `rag.parsing.mode` | `tika` | `tika` or `plain` |
| `rag.chunking.size` | `512` | Max chunk size (chars) |
| `rag.chunking.overlap` | `64` | Chunk overlap |
| `rag.chat.top-k` | `4` | Retrieval depth for `ask` |

Profiles: `local` (Ollama) and `cloud` (OpenAI). See the root `application.yml` and `docs/guides/tui-ingestion.md`.

## Testing

```bash
.\scripts\test.bat rag-tui
.\scripts\test.bat rag-tui --architecture
.\scripts\test.bat rag-tui --coverage   # enforces >= 85%
```

See the architecture rules in `ArchitectureTest`: `ui` may not reach into config/adapters/stores; `services` depend only on the `rag-common` strategy interfaces.
