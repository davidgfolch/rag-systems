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

codegraph_setup() {
  echo
  echo "===== CodeGraph auto-configuration ====="
  if ! command -v codegraph >/dev/null 2>&1; then
    echo "Installing CodeGraph CLI..."
    curl -fsSL https://raw.githubusercontent.com/colbymchenry/codegraph/main/install.sh | sh || { echo "Warning: CodeGraph CLI install failed. Skipping agent wiring."; return 0; }
    BIN_DIR="${CODEGRAPH_BIN_DIR:-$HOME/.local/bin}"
    case ":$PATH:" in
      *":$BIN_DIR:"*) ;;
      *) export PATH="$BIN_DIR:$PATH" ;;
    esac
  fi
  if ! command -v codegraph >/dev/null 2>&1; then
    echo "Warning: CodeGraph CLI not found on PATH after install. Skipping agent wiring."
    return 0
  fi
  echo "Wiring CodeGraph into configured agents..."
  codegraph install || echo "Warning: CodeGraph agent wiring failed."
  echo "Building CodeGraph index for this project..."
  codegraph init || echo "Warning: CodeGraph init failed."
}

if [ -z "${1:-}" ]; then
    echo "Installing all RAG modules..."
    mvn clean install -DskipTests -Djacoco.skip=true
else
    echo "Installing module: $1"
    mvn clean install -pl "apps/$1" -am -DskipTests -Djacoco.skip=true
fi
codegraph_setup

echo
echo "Install complete."
