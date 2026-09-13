# Observability for RAG Systems

This guide covers the observability stack for monitoring, tracing, and evaluating RAG pipelines.

## Overview

RAG systems have unique observability needs because answers depend on **retrieved context**, not just model behavior. Observability helps you answer:
- Where is the latency bottleneck? (embedding, search, or generation?)
- Are we retrieving the right chunks?
- Is the model hallucinating / faithful to context?
- What does this cost?

## Stack

| Component | Role |
|-----------|------|
| Micrometer | Metrics + tracing facade (Spring Boot standard) |
| OpenTelemetry | Distributed tracing (Micrometer bridge + OTLP exporter) |
| Tempo | Trace storage and querying |
| Loki | Log aggregation (loki4j pushes structured JSON) |
| Prometheus | Metrics storage |
| Grafana | Visualization, trace-to-log correlation |
| SLF4J + Logback | Structured logging with `traceId`/`spanId` MDC keys |

## Metrics Categories

### Ingestion
| Metric | Type | Description |
|--------|------|-------------|
| `rag_ingestion_documents_processed_total` | Counter | Documents ingested |
| `rag_ingestion_chunks_created_total` | Counter | Chunks created |
| `rag_ingestion_duration_seconds` | Histogram | Ingestion time |
| `rag_ingestion_errors_total` | Counter | Ingestion failures |

### Retrieval
| Metric | Type | Description |
|--------|------|-------------|
| `rag_retrieval_query_latency_seconds` | Histogram | End-to-end query latency |
| `rag_retrieval_vector_search_seconds` | Histogram | Vector search time |
| `rag_retrieval_chunks_returned` | Histogram | Chunks per query |
| `rag_retrieval_similarity_score` | Histogram | Score distribution |

### Generation
| Metric | Type | Description |
|--------|------|-------------|
| `rag_generation_tokens_input` | Counter | Input tokens |
| `rag_generation_tokens_output` | Counter | Output tokens |
| `rag_generation_latency_seconds` | Histogram | LLM response time |
| `rag_generation_cost_total` | Counter | Cost (provider-specific) |

### Quality (LLM-as-judge)
| Metric | Type | Description |
|--------|------|-------------|
| `rag_quality_relevance_score` | Gauge | Answer relevance |
| `rag_quality_faithfulness_score` | Gauge | Faithfulness to context |
| `rag_quality_hallucination_detected_total` | Counter | Hallucination events |
| `rag_quality_recall` | Gauge | Retrieval recall |

## Tracing

Every request gets a `traceId` following the full pipeline:

```
TUI command → REST/WebSocket (rag-basic) → rag-provider → LLM
```

Spans are started in the TUI (`tui.command`), propagated W3C-style via the `traceparent` header on every outbound HTTP `RestClient`/`WebSocketClient` request (including the `/ws/chat` handshake), extracted by the receiving module's MVC `ServerHttpObservationFilter`, and re-created on asynchronous ingestion/streaming worker threads via `TracePropagation.runWithSpan`. Each module's logs carry the same `traceId`/`spanId`, so a single trace correlates logs across all processes.

Each span captures:
- Input/output sizes
- Latency per stage
- Token counts
- Chunk retrieval counts + similarity scores

Configure sampling:
- Development: 100% (fast)
- Production: 10% (reduces overhead)

## Structured Logging

Logs are JSON with correlation IDs for joining with traces:

```json
{
  "timestamp": "2026-08-31T10:00:00Z",
  "level": "INFO",
  "traceId": "abc123",
  "spanId": "def456",
  "app": "rag-basic",
  "message": "ask: topK=4, sources=3, answerLength=25"
}
```

- Module MDC keys are `traceId`/`spanId` (camelCase, written by Micrometer) and flow into both the Logstash file/console encoders and the Loki4j appender.
- With the `observability` profile active, each app pushes structured JSON to Loki (`${LOKI_URL:-http://localhost:3100}/loki/api/v1/push`) with `app` and `level` labels.

### Trace ↔ Log correlation in Grafana

- **Trace → Logs**: open a trace in Tempo and click the **Logs** icon; Grafana's `tracesToLogs` mapping filters Loki by the `app` label and the selected trace's `traceId`.
- **Log → Trace**: every Loki log line embeds `"traceId":"<32-hex>"`; Grafana's derived-field regex `'"traceId":"([a-f0-9]{32})"'` turns it into a link that jumps to the matching Tempo trace.

## Setting Up Dashboards

### Start Observability Stack

```bash
# Windows
.\scripts\docker.bat up-obs

# Linux/Mac
./scripts/docker.sh up-obs
```

### Run with Observability Profile

```bash
# Windows
.\scripts\run.bat rag-basic --profile local,observability

# Linux/Mac
./scripts/run.sh rag-basic --profile local,observability
```

### Access
- **Tempo**: http://localhost:3200 (OTLP endpoint `http://localhost:4318/v1/traces`)
- **Prometheus**: http://localhost:9090
- **Loki**: http://localhost:3100 (push endpoint `/loki/api/v1/push`)
- **Grafana**: http://localhost:3000 (admin/admin)
- **Dashboards**: Prometheus queries at `/actuator/prometheus`

## Grafana Dashboards

Docker provisioning auto-loads three dashboards:

### 1. RAG Pipeline Overview
Request rate, latency (p95), error rates, active queries, pipeline stage counts.

### 2. Retrieval Performance
Vector search latency, chunks returned, similarity score distribution, recall.

### 3. Cost & Usage
Token usage by provider (OpenAI vs Ollama), input/output split, estimated cost.

## Benefits for Learning

- **Debugging**: Trace exactly which pipeline stage is the bottleneck
- **Comparison**: Quantitative metrics for comparing chunking/retrieval strategies
- **Optimization**: Identify which stage needs improvement
- **Cost control**: Track spending across OpenAI vs local Ollama

## Related

- [Getting Started](../guides/getting-started.md)
- [Performance Metrics](../comparison/performance-metrics.md)
