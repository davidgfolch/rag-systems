#!/usr/bin/env bash
# ===== RAG Systems Run Script (Linux/Mac) =====
# Usage: ./run.sh <module> [--profile <name>] [--args <spring args>]
#   ./run.sh rag-basic                 - Run with default (local) profile
#   ./run.sh rag-basic --profile cloud - Run with cloud profile
#   ./run.sh rag-basic --profile local,observability

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MODULE=""
PROFILES="local"
EXTRA_ARGS=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --profile) PROFILES="$2"; shift 2 ;;
        --args) EXTRA_ARGS="$2"; shift 2 ;;
        *) MODULE="$1"; shift ;;
    esac
done

if [ -z "$MODULE" ]; then
    echo "Error: module argument required."
    echo "Usage: ./run.sh <module> [--profile <name>]"
    exit 1
fi

echo "Running $MODULE with profile: $PROFILES"
mvn spring-boot:run -pl "apps/$MODULE" -am -Dspring-boot.run.profiles="$PROFILES" -Dspring-boot.run.arguments="$EXTRA_ARGS"
