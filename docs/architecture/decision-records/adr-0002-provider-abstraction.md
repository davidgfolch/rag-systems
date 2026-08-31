# ADR-0002: Provider Abstraction for Embeddings and LLMs

- **Status**: Accepted
- **Date**: 2026-08-31

## Context

RAG needs embedding generation and LLM text generation. Multiple providers exist (OpenAI, Anthropic, Ollama local, HuggingFace). To run locally AND compare against cloud frontier models, providers must be swappable.

## Decision

Use **interfaces** (`EmbeddingModel`, chat model wrapper) defined in `rag-common`, with provider implementations selected via **Spring configuration profiles** (`local` for Ollama, `cloud` for OpenAI). No provider-specific code in business services.

- `local` profile → Ollama (nomic-embed-text, phi4/qwen3)
- `cloud` profile → OpenAI (text-embedding-3-small, gpt-4o)

Swapping models is a **configuration change, not a code change**.

## Consequences

### Positive
- Run fully local on a regular machine or use cloud frontier models
- Compare local vs cloud quality/cost quantitatively
- No vendor lock-in

### Negative
- Provider-specific features may not be exposed through the common interface
- Cloud models need API keys/config (secrets management)
