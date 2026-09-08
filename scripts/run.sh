#!/usr/bin/env bash
# ===== RAG Systems Run Script (Linux/Mac) =====
# Usage: ./run.sh [<module>] [--profile <name>] [--args <spring args>] [--force]
#   ./run.sh                           - Run the TUI (default) with local profile
#   ./run.sh --profile cloud           - Run the TUI with cloud profile
#   ./run.sh rag-basic --profile cloud - Run another module
#   ./run.sh --force                   - Force full rebuild before running

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Bootstrap root .env files from scripts/.env*.example (idempotent)
bash scripts/bootstrap-env.sh

# Load .env (config) then .env.secrets (secrets override) into the environment
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

MODULE=""
PROFILES="local"
EXTRA_ARGS=""
FORCE=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --profile) PROFILES="$2"; shift 2 ;;
        --args) EXTRA_ARGS="$2"; shift 2 ;;
        --force) FORCE=true; shift ;;
        *) MODULE="$1"; shift ;;
    esac
done

if [ -z "$MODULE" ]; then
    MODULE="rag-tui"
fi

NEED_BUILD=true
if [ "$FORCE" = false ]; then
    COMMIT_TIME=$(git log -1 --format=%ct 2>/dev/null || echo "")
    if [ -n "$COMMIT_TIME" ]; then
        if [ -f "apps/.build-marker" ]; then
            MARKER_TIME=$(cat "apps/.build-marker")
            if [ "$MARKER_TIME" = "$COMMIT_TIME" ]; then
                NEED_BUILD=false
            fi
        fi
    fi
fi

if [ "$NEED_BUILD" = true ]; then
    echo "Building reactor (skip tests)..."
    mvn install -DskipTests -Djacoco.skip=true
    git log -1 --format=%ct >apps/.build-marker 2>/dev/null || true
else
    echo "No source changes detected, skipping build..."
fi
echo "Running $MODULE with profile: $PROFILES"
mvn spring-boot:run -pl "apps/$MODULE" -am -Dspring-boot.run.profiles="$PROFILES" -Dspring-boot.run.arguments="$EXTRA_ARGS"
