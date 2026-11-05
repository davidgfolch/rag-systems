#!/usr/bin/env bash
# ===== RAG Systems Install Script (Linux/Mac) =====
# Usage: ./install.sh [module] [--list]
#   ./install.sh          - Install all modules
#   ./install.sh rag-basic - Install specific module
#   ./install.sh --list   - List valid modules and exit
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
        echo "Hint: ./install.sh --list lists them." >&2
        exit 1
    fi
fi

# Bootstrap .env.secrets from scripts/.env.secrets.example (idempotent)
bash scripts/bootstrap-env.sh

# Sync docker postgres password to the generated PGVECTOR_PASSWORD secret (idempotent)
bash scripts/pg-pw.sh

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

if [ -z "$MODULE" ]; then
    echo "Installing all RAG modules..."
    mvn clean install -DskipTests -Djacoco.skip=true
else
    echo "Installing module: $MODULE"
    mvn clean install -pl "apps/$MODULE" -am -DskipTests -Djacoco.skip=true
fi
codegraph_setup

echo
echo "Install complete."
