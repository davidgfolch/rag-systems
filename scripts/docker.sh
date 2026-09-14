#!/usr/bin/env bash
# ===== RAG Systems Docker Script (Linux/Mac) =====
# Usage: ./docker.sh <command> [profile]
#   ./docker.sh up              - Start base services (PostgreSQL/pgvector)
#   ./docker.sh up-ollama       - Start base + Ollama (reuses an external Ollama if one is already running)
#   ./docker.sh up-obs          - Start base + observability (Prometheus, Grafana)
#   ./docker.sh up-sonar        - Start base + SonarQube
#   ./docker.sh up-all          - Start all services (obs + SonarQube + Ollama)
#   ./docker.sh down            - Stop all services
#   ./docker.sh logs            - View logs
#   ./docker.sh ps              - List running services
#
# If INFRA_MODE=auto (default) and an external Ollama already responds on
# OLLAMA_BASE_URL, the rag-ollama container is skipped and the external one is reused.
# Prometheus/Grafana host ports rotate to the next free port when their defaults
# (PROMETHEUS_PORT=9090, GRAFANA_PORT=3000) are already taken.

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Bootstrap root .env files from scripts/.env*.example (idempotent)
bash scripts/bootstrap-env.sh

# Load .env (config) then .env.secrets (secrets override) so docker-compose can interpolate ${VAR}
if [ -f .env ]; then
    set -a
    # shellcheck disable=SC1091
    source .env
    set +a
fi
if [ -f .env.secrets ]; then
    set -a
    # shellcheck disable=SC1091
    source .env.secrets
    set +a
fi

CMD="${1:-}"

if [ -z "$CMD" ]; then
    echo "Usage: ./docker.sh <command> [up|up-ollama|up-obs|up-sonar|up-all|down|logs|ps]"
    exit 1
fi

INFRA_MODE="${INFRA_MODE:-auto}"
OLLAMA_BASE_URL="${OLLAMA_BASE_URL:-http://localhost:11434}"
PROMETHEUS_PORT="${PROMETHEUS_PORT:-9090}"
GRAFANA_PORT="${GRAFANA_PORT:-3000}"

# Returns 0 (success) if an external Ollama responds at OLLAMA_BASE_URL.
is_external_ollama() {
    curl -sf --max-time 3 "$OLLAMA_BASE_URL/api/tags" >/dev/null 2>&1
}

# Returns 0 if the given host port is already in use (accepts a connection).
is_port_in_use() {
    (exec 3<>"/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1
}

# Prints the first free (unused, non-reserved) host port at or above $1, up to $1+10.
find_free_port() {
    local start="$1"
    local port
    for ((port = start; port <= start + 10; port++)); do
        if is_port_in_use "$port"; then
            continue
        fi
        echo "$port"
        return 0
    done
    return 1
}

# Moves PROMETHEUS_PORT/GRAFANA_PORT to free host ports when their defaults are taken.
rotate_obs_ports() {
    if ! PROMETHEUS_PORT="$(find_free_port "$PROMETHEUS_PORT")"; then
        echo "ERROR: no free port found for Prometheus starting at $PROMETHEUS_PORT" >&2
        return 1
    fi
    if ! GRAFANA_PORT="$(find_free_port "$GRAFANA_PORT")"; then
        echo "ERROR: no free port found for Grafana starting at $GRAFANA_PORT" >&2
        return 1
    fi
    export PROMETHEUS_PORT GRAFANA_PORT
}

COMPOSE_BASE="-f docker/docker-compose.yml"
COMPOSE_SONAR="-f docker/docker-compose.yml -f docker/docker-compose.sonarqube.yml"
COMPOSE_ALL="-f docker/docker-compose.yml -f docker/docker-compose.observability.yml -f docker/docker-compose.sonarqube.yml -f docker/docker-compose.ollama.yml"

SELECT_OLLAMA=0
SELECT_OBS=0
COMPOSE_SET="$COMPOSE_BASE"
OP="up -d"
case "$CMD" in
    up)
        ;;
    up-ollama)
        if [ "$INFRA_MODE" = "auto" ] && is_external_ollama; then
            echo "External Ollama detected at $OLLAMA_BASE_URL - reusing it, skipping rag-ollama"
        else
            SELECT_OLLAMA=1
        fi
        ;;
    up-obs)
        SELECT_OBS=1
        ;;
    up-sonar)
        COMPOSE_SET="$COMPOSE_SONAR"
        ;;
    up-all)
        SELECT_OBS=1
        if [ "$INFRA_MODE" = "auto" ] && is_external_ollama; then
            echo "External Ollama detected at $OLLAMA_BASE_URL - reusing it, skipping rag-ollama"
        else
            SELECT_OLLAMA=1
        fi
        ;;
    down)
        COMPOSE_SET="$COMPOSE_ALL"
        OP="down"
        ;;
    logs)
        COMPOSE_SET="$COMPOSE_ALL"
        OP="logs -f"
        ;;
    ps)
        COMPOSE_SET="$COMPOSE_ALL"
        OP="ps"
        ;;
    *)
        echo "Unknown command: $CMD"
        echo "Usage: ./docker.sh <command> [up|up-ollama|up-obs|up-sonar|up-all|down|logs|ps]"
        exit 1
        ;;
esac

if [ "$SELECT_OLLAMA" -eq 1 ]; then
    COMPOSE_SET="$COMPOSE_SET -f docker/docker-compose.ollama.yml"
fi
if [ "$SELECT_OBS" -eq 1 ]; then
    if ! rotate_obs_ports; then
        exit 1
    fi
    echo "Host ports -> Prometheus: $PROMETHEUS_PORT, Grafana: $GRAFANA_PORT (rotated to free ports if needed)"
    COMPOSE_SET="$COMPOSE_SET -f docker/docker-compose.observability.yml"
fi

echo "Running: docker compose $COMPOSE_SET $OP"
docker compose $COMPOSE_SET $OP

# Sync postgres password to the generated PGVECTOR_PASSWORD secret after any up
if [[ "$CMD" == up* ]]; then
    bash scripts/pg-pw.sh
fi
