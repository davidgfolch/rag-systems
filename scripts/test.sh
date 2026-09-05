#!/usr/bin/env bash
# ===== RAG Systems Test Script (Linux/Mac) =====
# Usage: ./test.sh [options] [module]
#   ./test.sh                     - Run all tests (incl. architecture tests)
#   ./test.sh --coverage          - Run tests with coverage (JaCoCo)
#   ./test.sh rag-basic           - Run only a specific module
#   ./test.sh rag-basic --coverage - Combine module + coverage
# Architecture tests (ArchitectureTest) always run as part of the test suite.

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Bootstrap root .env files from scripts/.env*.example (idempotent)
bash scripts/bootstrap-env.sh

COVERAGE_GOAL="test"
MODULE_SPEC=""

for arg in "$@"; do
    case "$arg" in
        --coverage) COVERAGE_GOAL="verify" ;;
        install) mvn install -DskipTests -Djacoco.skip=true >/dev/null 2>&1 ;;
        *) MODULE_SPEC="-pl apps/$arg -am" ;;
    esac
done

if [ -z "$MODULE_SPEC" ]; then
    echo "Running tests for all modules (goal: $COVERAGE_GOAL)..."
    mvn "$COVERAGE_GOAL"
else
    echo "Running tests for $MODULE_SPEC (goal: $COVERAGE_GOAL)..."
    mvn $MODULE_SPEC "$COVERAGE_GOAL"
fi

echo
echo "Test complete."
