---
name: rag-tui
description: Build and extend the interactive rag-tui module (terminal ingestion of files/webpages and RAG chat). Use when adding TUI commands, new document/web sources, or the interactive shell.
---

# RAG TUI Skill

Use this skill when creating or extending the interactive terminal module `apps/rag-tui`
that ingests documents/web pages and answers questions.

## Module Layout

```
apps/rag-tui/src/main/java/com/rag/tui/
├── RagTuiApplication.java       # Spring Boot entry point
├── config/RagTuiConfig.java     # wires strategies via properties (DIP)
├── ui/                          # terminal: CommandDispatcher (pure) + InteractiveShell (I/O)
├── services/                    # IngestionService, ChatService, FileDocumentLoader
├── fetching/                    # WebPageFetcher interface + JsoupWebPageFetcher
├── adapter/                     # SpringAiChatModel, SpringAiEmbeddingModel
├── chunking/ parsing/ vectorstore/  # concrete strategies (invoke rag-common interfaces)
└── src/test/java/com/rag/tui/...    # tests mirror production packages
```

## Architecture Rules (enforced by ArchUnit in ArchitectureTest)

- `ui` must not depend on `config`, `adapter`, `chunking`, `parsing`,
  `vectorstore`, or `com.rag.common.repositories`.
- `services` must not depend on `config`, `adapter`, `chunking`, `parsing`, `vectorstore`.
- Strategies must implement their `rag-common` interface
  (`WebPageFetcher`, `TextSplitter`, `DocumentParser`, `EmbeddingModel`, `ChatModel`, `VectorStore`).
- `config` is the only layer allowed to wire everything together.

## TDD Workflow

1. Write a failing unit test first (`should[Behavior]When[Condition]`), mocking all
   external dependencies (models, stores, network).
2. Implement the minimal code to pass it.
3. Verify with `.\scripts\test.bat rag-tui` and architecture with
   `.\scripts\test.bat rag-tui --architecture`.

## Unit-testing the shell and fetchers

- `InteractiveShell` takes `Reader`/`Writer` → drive it with `StringReader` +
  `StringWriter`.
- `JsoupWebPageFetcher` takes a mock jsoup `Connection` → no real network.
- Dispatcher tests mock `IngestionService`, `ChatService`, `WebPageFetcher`,
  and `FileDocumentLoader`.

## Adding a new source or command

1. Put the boundary behind an interface in `rag-common` (cross-module) or `fetching`/
   `services` (TUI-local).
2. Register the concrete bean in `RagTuiConfig`.
3. Add a handler in `CommandDispatcher` that builds a `Document` with source metadata
   and calls `ingestionService.ingest(...)` (or `chatService.ask(...)` for QA).
4. Add unit tests (dispatch + fetcher) and keep files ≤ 200 lines.

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `rag.vector-store.type` | `in-memory` | `in-memory` or `pgvector` |
| `rag.parsing.mode` | `tika` | `tika` or `plain` |
| `rag.chunking.size` / `.overlap` | `512` / `64` | chunking |
| `rag.chat.top-k` | `4` | retrieval depth for `ask` |

Profiles: `local` (Ollama), `cloud` (OpenAI).

## Definition of Done

- Tests pass: `.\scripts\test.bat rag-tui`
- Architecture passes: `.\scripts\test.bat rag-tui --architecture`
- Coverage ≥ 85%: `.\scripts\test.bat rag-tui --coverage`
- No file exceeds 200 lines; no circular dependencies; no hardcoded providers.
