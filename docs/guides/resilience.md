# Resilience: Concurrency, Timeouts & Retries

Ingesting a large site with `add-url <url> --all` used to submit every discovered
page at once. Each page ran on its own virtual thread, embedded chunks in windows
of 100, and called `rag-provider` through a blocking HTTP client with a short
timeout. The resulting burst overwhelmed Ollama's request queue and surfaced as
`HttpTimeoutException` in asynchronous ingestion jobs.

The pipeline now applies three safeguards at every network boundary:

1. **Bounded concurrency** — expensive work waits for a permit before running.
2. **Timeouts** — no call can wait forever; JDBC statements have query timeouts.
3. **Retries with backoff** — only transient failures of idempotent operations
   are retried, so a slow provider recovers instead of failing the whole crawl.

The shared primitives live in `rag-common-core` (`com.rag.common.core.resilience`):
`RetryPolicy`, `RetryExecutor`, `ConcurrencyLimiter`, `RetryableStatuses` and
`SqlRetry`.

## Defaults

| Boundary | Configuration | Default |
|----------|---------------|---------|
| Async ingestion submission | `rag.ingestion.max-concurrent` / `rag.ingestion.queue-wait` | 2 / 5m |
| Module embedding window | `rag.embedding.batch-size` | 32 |
| Provider embedding service | `rag.provider.embedding.max-concurrent` / `queue-wait` | 2 / 180s |
| Provider embedding batch | `rag.provider.embedding.batch-size` | 32 |
| Module → provider HTTP | `rag.provider.http.connect-timeout` / `request-timeout` | 10s / 180s |
| Provider → Ollama HTTP | `rag.provider.http.connect-timeout` / `read-timeout` | 10s / 240s |
| Webcrawler client | client factory | 5s connect / 60s read |
| Jsoup page fetch | `rag.crawler.fetch.timeout-seconds` | 20s |
| robots.txt / sitemap loader | per request | 15s |
| JDBC statements | adapter constant | 30s query timeout |
| Hikari pool | `HIKARI_CONNECTION_TIMEOUT` / `HIKARI_VALIDATION_TIMEOUT` / `HIKARI_KEEPALIVE_TIME` | 30000 / 5000 / 60000 ms |
| Retry schedule | `max-attempts` / `initial-backoff` / `max-backoff` | 4 / 500ms / 10s |

The retry policy uses a multiplier of `2.0` with `0.5` equal jitter, so retries
spread out instead of hammering a recovering service.

## What is retried

Retries are limited to **idempotent** operations and **transient** failures.

**HTTP** (`RetryableStatuses`): `408`, `425`, `429` and every `5xx`. Connection
resets, DNS failures and timeouts are retried. Other client errors (`400`, `401`,
`403`, `404`, `409`, `422`, …) fail immediately because repeating the request
cannot help.

**SQL** (`SqlRetry`): `SQLState` classes `08` (connection loss), `40` (deadlock /
serialization / rollback), `53` (insufficient resources) and `57P03` (cannot
connect now). Integrity violations (`23*`) and missing tables (`42P01`) are never
retried.

**Idempotent operations covered:**

- provider embeddings (`/api/embed` of the same input)
- crawler fetch, link ranking and discovery
- page fetch and robots/sitemap loading
- PgVector upsert (`ON CONFLICT (id) DO UPDATE`), document delete,
  document listing, connectivity probe and `CREATE INDEX IF NOT EXISTS`

**Not retried:** chat generation. Repeating it could duplicate a user-visible
answer or double-bill a provider call.

## Saturation behaviour

When the ingestion limiter cannot hand out a permit within `rag.ingestion.queue-wait`,
the job is marked `FAILED` with:

```
Ingestion capacity exhausted after waiting <ms> ms (max concurrent <n>)
```

The same applies to provider embeddings: when the provider's limiter is full it
returns HTTP `503` with `ProviderBusyException` instead of piling more work onto
Ollama. This is intentional backpressure — the crawl keeps scheduling, and each
page resolves as `DONE` or `FAILED` rather than hanging.

`add-url --all` still submits pages asynchronously, so the shell stays
responsive. The crawl step holds the session lock while submitting, which means
`add-url-cancel` can be delayed by up to the queue wait under heavy saturation.

## Ollama limits

Bounded clients only help if Ollama itself is bounded. The shipped container sets:

```yaml
OLLAMA_NUM_PARALLEL=2
OLLAMA_MAX_QUEUE=32
OLLAMA_MAX_LOADED_MODELS=1
OLLAMA_KEEP_ALIVE=5m
```

These values are mirrored in `.env`. If you point at an **external** Ollama,
configure the same variables in that service; otherwise requests still queue
inside Ollama and eventually time out.

## Tuning

- Keep `rag.provider.embedding.max-concurrent` at or below `OLLAMA_NUM_PARALLEL`;
  extra permits only move the queue into the provider.
- Raise `rag.ingestion.max-concurrent` only when the host has spare CPU/RAM (or
  GPU memory) for more than two embedding windows at a time.
- Lower `rag.provider.http.request-timeout` if you prefer a failure to surface
  sooner; raise it for very large documents, but keep it above the expected
  Ollama latency plus retry time.
- `HIKARI_MAX_POOL_SIZE` should stay modest — PgVector writes are already
  bounded by ingestion concurrency.

## Troubleshooting

| Symptom | Likely cause | Action |
|---------|--------------|--------|
| `Ingestion capacity exhausted` | More pages than the ingestion gate allows | Wait, lower crawl scope, or raise `rag.ingestion.max-concurrent` |
| HTTP `503` from rag-provider | Provider embedding limiter saturated | Lower crawl scope or raise `rag.provider.embedding.max-concurrent` |
| `HttpTimeoutException` | Ollama too slow or queue unbounded | Raise `read-timeout`, lower concurrency, set Ollama limits |
| Repeated retry warnings | Persistent downstream failure | Check container logs; retries only mask transient errors |
| `42P01` missing table | Nothing ingested into the module schema yet | Expected; list/delete return empty |

## Future alternative: durable queue

Bounded submission keeps memory use predictable but is **in-process**. A restart
loses queued pages, and there is no whole-document retry. If durability or
at-least-once delivery is ever required, the recommended approach is a
PostgreSQL-backed job table using `SELECT … FOR UPDATE SKIP LOCKED` — it reuses
the database already deployed and avoids the operational cost of Kafka or
RabbitMQ for this low-throughput workload.

That work is deliberately **not implemented**. Note that chunk IDs are generated
per chunking run (`UUID.randomUUID()`), so redelivering a partially ingested
document creates new rows instead of overwriting the old chunks. A durable queue
would need deterministic chunk IDs or a per-document ingestion generation marker
first.

## Verification

```bash
./scripts/test.sh          # unit + architecture tests, coverage gates
./scripts/dev.sh check all # tests + SonarQube
```

Then reproduce the original scenario with `add-url <url> --all`; pages should
complete (or fail fast) without `HttpTimeoutException` storms.
