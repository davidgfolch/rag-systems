# ADR-0013: Split rag-common into Capability Modules

- **Status**: Accepted
- **Date**: 2026-09-21

## Context

The single `rag-common` library aggregated four unrelated concerns: core domain models and strategy ports, ingestion (chunking/parsing), retrieval (vector stores), and generation (chat/embedding adapters). Every consumer therefore inherited the union of their transitive dependencies (`tika`, `spring-ai-vector-store`, `spring-ai-client-chat`, `reactor-core`) even when it only needed a couple of interfaces. A light consumer such as `rag-tui` pulled the whole `spring-ai`/`tika`/`reactor` stack for nothing.

The kernel was also not truly provider-agnostic at the reactive level: `ChatModelPort` exposed a streaming method returning `Flux<String>`, so the domain/core abstraction dragged Project Reactor into modules that never stream.

## Decision

Split `rag-common` into four Maven modules under `apps/`, each owning one business capability and published with a `test-jar`:

- **`rag-common-core`** (`com.rag.common.core.*`) - kernel: domain (`Chunk`, `Document`, `DocumentSummary`, `MetadataKeys`, `FloatConversions`), strategy ports (`DocumentParser`, `TextSplitter`, `EmbeddingModelPort`, `ChatModelPort`, `VectorStorePort`), `TracePropagation`, `FileDocumentLoader`. Depends only on `spring-boot-starter` + `micrometer-tracing`; **reactor-free**.
- **`rag-common-ingestion`** (`com.rag.common.ingestion.*`) - chunkers (`FixedSizeChunker`, `RecursiveCharacterChunker`, `TokenChunker`), parsers (`PlainTextParser`, `TikaDocumentParser`), `IngestionService`, `AsyncIngestionService`. The only module depending on Apache Tika.
- **`rag-common-retrieval`** (`com.rag.common.retrieval.*`) - `InMemoryVectorStore`, `PgVectorStoreAdapter`. The only module depending on `spring-ai-vector-store`.
- **`rag-common-generation`** (`com.rag.common.generation.*`) - `ChatService`, `ProviderHttpClient`, `RemoteChatModelPort`, `RemoteEmbeddingModel`, `SpringAiChatModel`, `SpringAiEmbeddingModel`. The only module depending on `reactor-core` and the Spring AI chat/embedding clients.

**Streaming port split.** `ChatModelPort` (core) is blocking only: `String complete(String)`. A new `StreamingChatModelPort` (generation) owns `Flux<String> completeStream(String)`. `RemoteChatModelPort` and `SpringAiChatModel` implement both; `ChatService` depends on both ports. This keeps the kernel free of reactor while the streaming adapter must implement both.

**Dependency DAG.** `rag-common-core` is the root; `ingestion`, `retrieval` and `generation` depend on it and never on each other. Consumers declare only the capabilities they use and consume the others' `test-jar` (fixtures, `PostgresContainerConfig`) at `test` scope:

- `rag-basic`, `rag-advanced`, `rag-agentic` - all four (+ core/ingestion/retrieval test-jars)
- `rag-tui`, `rag-cli`, `rag-evaluation`, `rag-webcrawler`, `rag-memory`, `rag-provider` - `rag-common-core` only (+ core/webcrawler test-jars)
- `rag-observability` - no `rag-common` dependency

**Regression guard.** The four capability modules bind `maven-dependency-plugin:analyze-only` to `verify` with `failOnWarning`, so a capability that starts using a dependency it does not declare (or declares one it no longer uses) fails the build. Java 25 bytecode requires `maven-dependency-plugin` 3.11.0+.

Architecture (ArchUnit) tests moved with the code: repository-hygiene and cross-module rules live in `rag-common-core`; each capability module keeps its own layer/logger/dependency rules.

## Consequences

### Positive
- The kernel is reactor-free; light consumers no longer inherit `tika`, `spring-ai-vector-store`, `spring-ai-client-chat` or `reactor-core`.
- Capability boundaries are enforced twice: ArchUnit layer rules and the `analyze-only` dependency guard.
- Each capability evolves, versions and is tested in isolation with its own 90% coverage gate.

### Negative
- Four modules instead of one, with `test-jar` wiring between them and their consumers.
- Consumers must list each capability they use rather than a single `rag-common` coordinate.

## Related

- [ADR-0001: Monorepo Layout with Decoupled Modules](adr-0001-monorepo-layout.md)
- [ADR-0002: Provider Abstraction for Embeddings and LLMs](adr-0002-provider-abstraction.md)
- [ADR-0010: Dedicated rag-provider Service for Model Switching](adr-0010-rag-provider.md)
