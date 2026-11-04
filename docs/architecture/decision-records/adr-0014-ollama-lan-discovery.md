# ADR-0014: Ollama LAN Discovery

- **Status**: Accepted
- **Date**: 2026-11-04

## Context

`rag-provider` owns every Ollama endpoint in the monorepo, but the endpoint is
pinned through `OLLAMA_BASE_URL` (default `http://localhost:11434`). When Ollama
runs on another machine on the LAN — the common case for a shared GPU box — the
user must look up its IP and edit `.env`. A sibling project
(`ai_job_search/apps/commonlib/commonlib/network`) already solves this with a
LAN scan, a verified-host cache and an `auto`/`discover` URL spec. This ADR
brings the same behaviour to the Java provider service.

At the same time, boot must stay offline and fast: CI, containers and
`RagProviderApplicationContextTest` cannot depend on Ollama being reachable.

## Decision

Add LAN discovery to **`rag-provider`** only (it already owns the Ollama
client, registry and wiring; the `rag-common-*` kernel stays network-free).

**Activation.** `OLLAMA_BASE_URL=auto` or `discover` enables discovery.
Explicit URLs are used as-is and never probed or scanned, so the default boot
performs no network I/O. `local`/`localhost`/`127.0.0.1` resolve to loopback
without a scan. `OLLAMA_DISCOVERY_ENABLED=false` makes `auto` fall straight
back to loopback.

**Resolution order** (`OllamaEndpointResolver` + `OllamaDiscoveryService`):

1. fresh positive cache entry that still answers `GET /api/tags` → reuse it;
2. fresh negative cache entry → skip the scan and fall back;
3. probe `127.0.0.1:${OLLAMA_DISCOVERY_PORT}`;
4. scan the LAN and verify every open port with `GET /api/tags`;
5. nothing found → warn and fall back to `http://127.0.0.1:11434`, so an
   Ollama started later still works without a restart.

**Scan.** `LanScanner` probes the machine's site-local `/24`, its adjacent
`/24`s, the common `192.168.0/24` + `192.168.1.0/24` LANs and any
`OLLAMA_DISCOVERY_SUBNETS` CIDRs. TCP connects run on virtual threads behind a
100-permit cap; `OLLAMA_DISCOVERY_MAX_HOSTS` (default 1024) bounds the total.

**Cache.** `data/discovery/cache.json` stores `{host, ts}` per service kind.
Positive entries live 3600 s, negative entries 60 s. Writes use a temp file plus
atomic replace; malformed payloads are ignored. Discovered hosts are **never**
written to `.env` or `data/provider-profiles.json`.

**Docker scripts.** `scripts/docker.sh` / `docker.bat` treat `auto`/`discover`
as "no explicit external URL", so `up-ollama` starts the local `rag-ollama`
container instead of curling an invalid spec.

### Deliberate differences from the Python reference

- **In-process lock only.** The Python cache takes a cross-process file lock
  because several containers share it. One `rag-provider` process owns
  discovery here, so a JVM lock is sufficient and avoids stale-lock handling.
- **No REST endpoint or TUI command.** Discovery is silent resolution at boot,
  matching the "zero new surface" goal; changing `OLLAMA_BASE_URL` and
  restarting switches modes.
- **No IPv6 scanning.** Ollama on the LAN is reached over IPv4; IPv6 adds
  link-local scope handling for little benefit.
- **Separate `OLLAMA_DISCOVERY_PORT`.** Docker's `OLLAMA_PORT` is the published
  host port of the `rag-ollama` container; discovery must probe the port the
  server actually listens on and must not inherit that override.

## Consequences

### Positive
- A shared Ollama on the LAN needs no manual IP configuration.
- Explicit URLs keep boot offline and deterministic; tests stay isolated.
- The scan runs at most once per TTL thanks to the cache, and negative results
  are forgotten quickly.
- No new Maven dependencies: JDK `HttpClient`, `java.net` sockets and the
  already-present Jackson.

### Negative
- An `auto` boot can add a few seconds when no Ollama is reachable (bounded by
  the timeouts and host budget).
- Inside Docker the container only sees the Docker bridge; the host LAN must be
  listed in `OLLAMA_DISCOVERY_SUBNETS`.
- Discovery is IPv4-only and does not use mDNS, so non-`/24` networks must be
  listed explicitly.

## Related

- [ADR-0003: Local-first Development with Ollama](adr-0003-local-first.md)
- [ADR-0010: Dedicated rag-provider Service for Model Switching](adr-0010-rag-provider.md)
- [ADR-0002: Provider Abstraction for Embeddings and LLMs](adr-0002-provider-abstraction.md)
