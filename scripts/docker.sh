#!/usr/bin/env bash
# ===== RAG Systems Docker Script (Linux/Mac) =====
# Usage: ./docker.sh <command> [profile]
#   ./docker.sh up              - Start base services (PostgreSQL/pgvector)
#   ./docker.sh up-obs          - Start base + observability (Prometheus, Grafana)
#   ./docker.sh up-sonar        - Start base + SonarQube
#   ./docker.sh up-all          - Start all services (obs + SonarQube)
#   ./docker.sh down            - Stop all services
#   ./docker.sh logs            - View logs
#   ./docker.sh ps              - List running services

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Bootstrap root .env files from scripts/.env*.example (idempotent)
bash scripts/bootstrap-env.sh

CMD="${1:-}"

if [ -z "$CMD" ]; then
    echo "Usage: ./docker.sh <command> [up|up-obs|up-sonar|up-all|down|logs|ps]"
    exit 1
fi

COMPOSE_BASE="-f docker/docker-compose.yml"
COMPOSE_OBS="-f docker/docker-compose.yml -f docker/docker-compose.observability.yml"
COMPOSE_SONAR="-f docker/docker-compose.yml -f docker/docker-compose.sonarqube.yml"
COMPOSE_ALL="-f docker/docker-compose.yml -f docker/docker-compose.observability.yml -f docker/docker-compose.sonarqube.yml"

case "$CMD" in
    up)
        echo "Starting base services (PostgreSQL)..."
        docker compose $COMPOSE_BASE up -d
        ;;
    up-obs)
        echo "Starting base + observability services..."
        docker compose $COMPOSE_OBS up -d
        ;;
    up-sonar)
        echo "Starting base + SonarQube services..."
        docker compose $COMPOSE_SONAR up -d
        ;;
    up-all)
        echo "Starting all services (observability + SonarQube)..."
        docker compose $COMPOSE_ALL up -d
        ;;
    down)
        echo "Stopping services..."
        docker compose $COMPOSE_ALL down
        ;;
    logs)
        docker compose $COMPOSE_ALL logs -f
        ;;
    ps)
        docker compose $COMPOSE_ALL ps
        ;;
    *)
        echo "Unknown command: $CMD"
        echo "Usage: ./docker.sh <command> [up|up-obs|up-sonar|up-all|down|logs|ps]"
        exit 1
        ;;
esac
