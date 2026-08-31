# rag-observability

Tracing, metrics, structured logging, and dashboards for all RAG modules.

## Purpose

Provide cross-cutting observability so RAG pipelines can be monitored, debugged, and compared quantitatively. See [observability guide](../../docs/guides/observability.md) for full details.

## Structure

```
com.rag.observability
├── config/    - OpenTelemetry, Micrometer configuration
├── metrics/   - Ingestion, Retrieval, Generation, Quality metrics
├── tracing/   - RAG pipeline span definitions
└── logging/   - Structured JSON logging with traceId/spanId
```

## Key Metrics

| Category | Examples |
|----------|----------|
| Ingestion | documents processed, chunks created, duration, errors |
| Retrieval | query latency, vector search latency, chunks returned, similarity |
| Generation | input/output tokens, LLM latency, cost |
| Quality | relevance, faithfulness, hallucination events |

## Dashboards

Grafana provisioning (in `docker/grafana/`) auto-loads:
1. RAG Pipeline Overview
2. Retrieval Performance
3. Cost & Usage

## Enabling

```bash
# Windows
.\scripts\docker.bat up-obs
.\scripts\run.bat rag-basic --profile local,observability

# Linux/Mac
./scripts/docker.sh up-obs
./scripts/run.sh rag-basic --profile local,observability
```

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)

## Testing

```bash
.\scripts\test.bat rag-observability
```