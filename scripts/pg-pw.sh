#!/usr/bin/env bash
# ===== Sync rag-postgres password to the PGVECTOR_PASSWORD secret (Linux/Mac) =====
# Idempotent: runs only when the rag-postgres container is running; skipped otherwise.
# Called by install.sh and docker.sh so a checkout's postgres matches .env.secrets.

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ -z "${PGVECTOR_PASSWORD:-}" ] && [ -f .env.secrets ]; then
    PGVECTOR_PASSWORD="$(grep '^PGVECTOR_PASSWORD=' .env.secrets | head -1 | cut -d= -f2- || true)"
fi

if [ -z "${PGVECTOR_PASSWORD:-}" ]; then
    echo "pg-pw: PGVECTOR_PASSWORD is empty; run scripts/bootstrap-env.sh first."
    exit 0
fi

if ! command -v docker >/dev/null 2>&1; then
    echo "pg-pw: docker not installed; postgres password sync skipped."
    exit 0
fi

RUNNING="$(docker ps --filter "name=rag-postgres" --filter "status=running" --format "{{.Names}}" 2>/dev/null || true)"
if [ -z "$RUNNING" ]; then
    echo "pg-pw: rag-postgres not running; sync skipped (applied on first docker up)."
    exit 0
fi

if ! docker exec rag-postgres psql -U rag -d rag -qc "ALTER USER rag WITH PASSWORD '$PGVECTOR_PASSWORD';" >/dev/null 2>&1; then
    echo "pg-pw: WARNING could not update rag-postgres password (container may be unhealthy)."
    exit 0
fi
echo "pg-pw: rag-postgres password synced to PGVECTOR_PASSWORD in .env.secrets."