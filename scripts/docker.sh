#!/usr/bin/env bash
# ===== RAG Systems Docker Script (Linux/Mac) =====
# Usage: ./docker.sh <command> [profile]
#   ./docker.sh up              - Start base services (PostgreSQL/pgvector)
#   ./docker.sh up-obs          - Start base + observability (Prometheus, Grafana)
#   ./docker.sh down            - Stop services
#   ./docker.sh logs            - View logs
#   ./docker.sh ps              - List running services

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

CMD="${1:-}"

if [ -z "$CMD" ]; then
    echo "Usage: ./docker.sh <command> [up|up-obs|down|logs|ps]"
    exit 1
fi

case "$CMD" in
    up)
        echo "Starting base services (PostgreSQL)..."
        docker compose -f docker/docker-compose.yml up -d
        ;;
    up-obs)
        echo "Starting base + observability services..."
        docker compose -f docker/docker-compose.yml -f docker/docker-compose.observability.yml up -d
        ;;
    down)
        echo "Stopping services..."
        docker compose -f docker/docker-compose.yml -f docker/docker-compose.observability.yml down
        ;;
    logs)
        docker compose -f docker/docker-compose.yml -f docker/docker-compose.observability.yml logs -f
        ;;
    ps)
        docker compose -f docker/docker-compose.yml -f docker/docker-compose.observability.yml ps
        ;;
    *)
        echo "Unknown command: $CMD"
        echo "Usage: ./docker.sh <command> [up|up-obs|down|logs|ps]"
        exit 1
        ;;
esac
