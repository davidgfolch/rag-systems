#!/usr/bin/env bash
# ===== RAG Systems Build Script (Linux/Mac) =====
# Usage: ./build.sh [module]
#   ./build.sh           - Build all modules
#   ./build.sh rag-basic - Build a specific module

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Bootstrap .env.secrets from scripts/.env.secrets.example (idempotent)
bash scripts/bootstrap-env.sh

if [ -z "${1:-}" ]; then
    echo "Building all RAG modules..."
    mvn clean package
else
    echo "Building module: $1"
    mvn clean package -pl "apps/$1" -am
fi

echo
echo "Build complete."
