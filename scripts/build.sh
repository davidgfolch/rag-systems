#!/usr/bin/env bash
# ===== RAG Systems Build Script (Linux/Mac) =====
# Usage: ./build.sh [module] [--list]
#   ./build.sh           - Build all modules
#   ./build.sh rag-basic - Build a specific module
#   ./build.sh --list    - List valid modules and exit
# <module> accepts a bare name (rag-basic) or a path form (apps/rag-basic).

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MODULE=""
if [ "${1:-}" = "--list" ]; then
    echo "Valid modules:"
    bash "$ROOT/scripts/modules.sh" list
    exit 0
fi
case "${1:-}" in
    -*) echo "Unknown option: $1" >&2; exit 2 ;;
    *) MODULE="${1:-}" ;;
esac
if [ -n "$MODULE" ]; then
    MODULE="${MODULE#apps/}"
    MODULE="${MODULE%/}"
    if ! bash "$ROOT/scripts/modules.sh" check "$MODULE"; then
        echo "Unknown module: $MODULE" >&2
        echo "Valid modules:" >&2
        bash "$ROOT/scripts/modules.sh" list | sed 's/^/  /' >&2
        echo "Hint: ./build.sh --list lists them." >&2
        exit 1
    fi
fi

# Bootstrap .env.secrets from scripts/.env.secrets.example (idempotent)
bash scripts/bootstrap-env.sh

if [ -z "$MODULE" ]; then
    echo "Building all RAG modules..."
    mvn clean package
else
    echo "Building module: $MODULE"
    mvn clean package -pl "apps/$MODULE" -am
fi

echo
echo "Build complete."
