# rag-observability

Cross-cutting **tracing, metrics, structured logging, and Grafana dashboards** for every RAG module. A **library** (shared config + dependencies) — no runnable app, no HTTP port. Modules opt in by adding the `observability` Spring profile.

## Purpose

RAG answers depend on retrieved context, so you need to see *where* latency/cost/quality comes from — not just that a request succeeded. This module makes every pipeline observable so you can **debug** bottlenecks and, crucially, **compare modules quantitatively** (rag-basic vs rag-advanced vs rag-agentic) rather than by anecdote.

Full reference: [docs/guides/observability.md](../../docs/guides/observability.md).

## Contents

There is no Java source — this module supplies shared **configuration and dependencies**, which runtime apps include and activate with the `observability` profile:

```
rag-observability
├── src/main/resources/
│   ├── application-observability.yml   - Actuator/Prometheus exposure, OTLP trace export, sampling
│   └── logback-spring.xml              - structured JSON logging + Loki4j appender
└── pom.xml                             - Micrometer, OpenTelemetry, logstash-encoder, loki4j deps
```

| Piece | Role |
|-------|------|
| Micrometer + OTel bridge | Metrics + distributed tracing, exported over OTLP to Tempo |
| Prometheus registry | Scrapes `/actuator/prometheus` |
| logstash-encoder | Structured JSON logs with `traceId`/`spanId` |
| Loki4j appender | Pushes logs to Loki with `app`/`level` labels |

## Quick access (when the stack is up)

| Service | URL | Credentials |
|---------|-----|-------------|
| Grafana dashboards | <http://localhost:3000> | `admin` / `admin` |
| Prometheus (metrics) | <http://localhost:9090> | - |
| Tempo (traces) | <http://localhost:3200> | - |
| Loki (logs) | <http://localhost:3100> | - |
| Per-app metrics | `http://localhost:<port>/actuator/prometheus` | e.g. rag-basic `:8081` |

Ports auto-rotate if busy; `up-obs` prints the actual URLs on start.

## Metric families

Defined in [docs/guides/observability.md](../../docs/guides/observability.md) and read by the auto-loaded dashboards:

| Category | Metric families | What they tell you |
|----------|-----------------|--------------------|
| Ingestion | `rag_ingestion_documents_processed_total`, `rag_ingestion_chunks_created_total`, `rag_ingestion_duration_seconds`, `rag_ingestion_errors_total` | How fast/how many docs become chunks |
| Retrieval | `rag_retrieval_query_latency_seconds`, `rag_retrieval_vector_search_seconds`, `rag_retrieval_chunks_returned`, `rag_retrieval_similarity_score`, `rag_retrieval_query_total` | Whether search (not generation) is the bottleneck, and how good the matches look |
| Generation | `rag_generation_tokens_total`, `rag_generation_tokens_input/output`, `rag_generation_latency_seconds`, `rag_generation_cost_total`, `rag_generation_errors_total` | Token spend and LLM latency/cost by provider |
| Quality | `rag_quality_relevance_score`, `rag_quality_faithfulness_score`, `rag_quality_recall`, `rag_quality_hallucination_detected_total` | Whether the answer is grounded and the retrieval is complete |
| Pipeline | `rag_pipeline_stages_total` | Counts by stage, to see where time is spent |

## How to compare modules (the "how & why")

The goal is a fair A/B: **same corpus, same questions, same profile — only the module under test changes.** Each module runs in its own PgVector schema (`rag_basic`, `rag_advanced`, ...) so nothing bleeds between them.

1. **Bring up the stack and a provider**

   ```bash
   .\scripts\docker.bat up          # PostgreSQL
   .\scripts\docker.bat up-obs      # Prometheus, Grafana, Tempo, Loki
   .\scripts\run.bat rag-provider --profile local
   ```

2. **Start the two modules with the observability profile** (separate terminals):

   ```bash
   .\scripts\run.bat rag-basic    --profile local,observability
   .\scripts\run.bat rag-advanced --profile local,observability
   ```

3. **Load the identical corpus into both** (`:8081` and `:8082`) and **send identical questions** to each `/api/query`:

   ```bash
   for port in 8081 8082; do
     curl -F "file=@docs/report.pdf" http://localhost:$port/api/documents/ingest-file
     curl -H "Content-Type: application/json" \
       -d '{"question":"What does the document say?","topK":4}' \
       http://localhost:$port/api/query
   done
   ```

4. **Compare on three axes:**

   | Axis | Where to look | What a "better" module looks like |
   |------|---------------|-----------------------------------|
   | **Latency & throughput** | Grafana → *RAG Pipeline Overview* (`rag_retrieval_query_latency_seconds`, `rag_retrieval_vector_search_seconds`) | Lower p95 end-to-end and vector-search time |
   | **Retrieval quality** | Grafana → *Retrieval Performance* (`rag_retrieval_similarity_score`, `rag_retrieval_chunks_returned`, `rag_quality_recall`) | Higher similarity/recall at the same `topK` |
   | **Cost & tokens** | Grafana → *Cost & Usage* (`rag_generation_tokens_*`, `rag_generation_cost_total` by `provider`) | Fewer input tokens for equal quality (compression/contextual filtering) |
   | **Trace-level cause** | Tempo — open one request's trace to see stage-by-stage spans (ingest vs search vs generate) | Reranking may add a span but reduce wrong answers |
   | **Logs** | Loki — filter by `app`, jump to a trace via the `traceId` derived field | Fewer warnings/errors per request |

5. **Correlate one request end to end:** every log line carries `traceId`/`spanId`. In Loki click the traceId → jumps to the Tempo trace → Tempo's **Logs** view jumps back. Use this to explain a bad score (e.g. "the similarity was fine, generation hallucinated").

6. **Back it with published numbers:** record/compare your p95 + scores against [performance-metrics.md](../../docs/comparison/performance-metrics.md) and the quality harness in [rag-evaluation](../rag-evaluation/README.md).

**Why this matters:** without this, you'd be guessing whether hybrid search or reranking actually helped. The dashboards give the *what* (a number moved), the trace gives the *where* (which stage), and the logs give the *why* (what happened inside it).

## Dashboards (auto-loaded from `docker/grafana/`)

| Dashboard | Answers |
|-----------|---------|
| **RAG Pipeline Overview** | Is the system healthy? Request rate, p95 latency, errors, stage counts |
| **Retrieval Performance** | Is retrieval fast and accurate? Vector-search latency, chunks returned, similarity, recall |
| **Cost & Usage** | What does it cost? Token usage by provider, input/output split, estimated spend |

Trace ↔ log correlation works both ways: Tempo's `tracesToLogs` and Loki's `traceId` derived field.

## Enabling

```bash
# Windows
.\scripts\docker.bat up-obs
.\scripts\run.bat rag-basic --profile local,observability

# Linux/Mac
./scripts/docker.sh up-obs
./scripts/run.sh rag-basic --profile local,observability
```

`observability` activates `application-observability.yml`: 100% trace sampling, OTLP export to `http://localhost:4318/v1/traces`, Prometheus export, and structured JSON logging to Loki (`${LOKI_URL:-http://localhost:3100}`).

## Testing

```bash
.\scripts\test.bat rag-observability
```

## Related

- [Observability guide (full detail)](../../docs/guides/observability.md)
- [Performance metrics](../../docs/comparison/performance-metrics.md) — recorded comparison numbers
- [rag-evaluation](../rag-evaluation/README.md) — quality benchmark harness
- [rag-basic](../rag-basic/README.md) / [rag-advanced](../rag-advanced/README.md) — the modules to compare