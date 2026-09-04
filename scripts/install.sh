#!/usr/bin/env bash
# ===== RAG Systems Install Script (Linux/Mac) =====
# Usage: ./install.sh [module]
#   ./install.sh          - Install all modules
#   ./install.sh rag-basic - Install specific module

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Bootstrap root .env files from scripts/.env*.example (idempotent)
bash scripts/bootstrap-env.sh

if [ -z "${1:-}" ]; then
    echo "Installing all RAG modules..."
    mvn clean install -DskipTests
else
    echo "Installing module: $1"
    mvn clean install -pl "apps/$1" -am -DskipTests
fi

echo
echo "Install complete."
