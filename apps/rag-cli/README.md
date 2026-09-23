# rag-cli

> **Status: placeholder.** The module is wired into the reactor (pom only) but has **no implementation source yet**. The features below are the intended design.

Interactive command-line tool for querying RAG systems without a web UI.

## Purpose

Provide a simple way to test RAG queries interactively or in batches using a terminal. Ideal for learning and quick experimentation after development.

## Features

| Feature | Description |
|---------|-------------|
| Interactive mode | Type queries, get answers with source chunks |
| Batch mode | Run a file of queries, output results |
| Source display | Show which chunks informed each answer |
| Profile support | local (Ollama) or cloud (OpenAI) |

## Running

> Not implemented yet — shown for the intended behavior.

```bash
# Windows
.\scripts\run.bat rag-cli --profile local

# Linux/Mac
./scripts/run.sh rag-cli --profile local
```

Interactive commands:
```
> What is Spring AI?
> :chunks 5        # show top 5 retrieved chunks
> :stats           # show query metrics
> :quit
```

## Why not a web UI?

A terminal interface avoids the overhead of building/maintaining a frontend while keeping the focus on the RAG implementation. For richer exploration, use the REST API directly (see the runnable module READMEs) — the API contract is defined once in [rag-contract](../rag-contract/README.md).

## Related

- [rag-tui](../rag-tui/README.md) — the interactive terminal UI that already exists today

## Testing

```bash
.\scripts\test.bat rag-cli
```