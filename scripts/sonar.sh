#!/usr/bin/env bash
# ===== RAG Systems SonarQube Analysis Script (Linux/Mac) =====
# Usage: ./sonar.sh <command> [token]
#   ./sonar.sh up              - Start SonarQube via Docker
#   ./sonar.sh scan [token]    - Run tests + JaCoCo + SonarQube analysis
#   ./sonar.sh up-scan [token] - Start SonarQube, wait for ready, then scan
#   ./sonar.sh down            - Stop SonarQube
# Token: passed as 2nd arg or set SONAR_TOKEN env var.

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

CMD="${1:-}"
TOKEN="${2:-${SONAR_TOKEN:-}}"

if [ -z "$CMD" ]; then
    echo "Usage: ./sonar.sh <command> [up|scan|up-scan|down] [token]"
    exit 1
fi

COMPOSE_BASE="-f docker/docker-compose.yml"
COMPOSE_SONAR="-f docker/docker-compose.yml -f docker/docker-compose.sonarqube.yml"
SCAN_ARGS="-Dsonar.host.url=http://localhost:9000 -Dsonar.scanner.skipJreProvisioning=true -DskipTests=false"

export_results() {
    echo "Exporting SonarQube results to README.md..."
    bash scripts/sonar-export.sh
}

case "$CMD" in
    up)
        echo "Starting SonarQube..."
        docker compose $COMPOSE_SONAR up -d
        echo
        echo "SonarQube starting at http://localhost:9000"
        echo "Default credentials: admin / admin"
        echo "Wait ~30 seconds for the server to be ready before running scan."
        ;;
    scan)
        if [ -z "$TOKEN" ]; then
            echo "Error: no token. Pass it as an argument or set SONAR_TOKEN."
            exit 1
        fi
        echo "Running tests with coverage + SonarQube analysis..."
        mvn verify sonar:sonar -Dsonar.token="$TOKEN" $SCAN_ARGS
        export_results
        ;;
    up-scan)
        echo "Starting SonarQube..."
        docker compose $COMPOSE_SONAR up -d
        echo "Waiting for SonarQube to be ready..."
        until curl -sf http://localhost:9000/api/system/status > /dev/null 2>&1; do
            sleep 5
        done
        echo "SonarQube is ready."
        if [ -z "$TOKEN" ]; then
            echo "Error: no token. Pass it as an argument or set SONAR_TOKEN."
            exit 1
        fi
        echo "Running tests with coverage + SonarQube analysis..."
        mvn verify sonar:sonar -Dsonar.token="$TOKEN" $SCAN_ARGS
        export_results
        ;;
    down)
        echo "Stopping SonarQube..."
        docker compose $COMPOSE_SONAR down
        ;;
    *)
        echo "Unknown command: $CMD"
        echo "Usage: ./sonar.sh <command> [up|scan|up-scan|down] [token]"
        exit 1
        ;;
esac

echo
echo "SonarQube operation complete."
