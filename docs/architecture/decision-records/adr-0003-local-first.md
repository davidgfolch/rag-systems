# ADR-0003: Local-First Development with Ollama

- **Status**: Accepted
- **Date**: 2026-08-31

## Context

The project must be runnable on a regular local machine for learning purposes. Using only cloud APIs would add cost, require network, and send learning data to third parties. But RAG separates into layers with very different hardware demands.

## Decision

Make **local (Ollama) the default development profile**, recognizing that RAG layers have different hardware needs:

- **Embeddings** (retrieval engine): small models run fast on CPU (`nomic-embed-text`, ~300MB). No GPU needed.
- **Vector store** (PgVector): runs locally on CPU.
- **LLM generation**: optional GPU. Use small models (phi4, qwen3:4b) on CPU; qwen3:8b with an 8GB GPU.

Provider abstraction (ADR-0002) lets developers switch to cloud models when frontier reasoning is needed.

## Consequences

### Positive
- Zero-cost, private, offline learning
- Works on a regular machine with 16GB RAM
- Compare local vs cloud quality/cost

### Negative
- Local LLM quality/reasoning is below frontier models
- CPU-only generation is slow (several secs/token for larger models)
- GPU recommended for comfortable generation
