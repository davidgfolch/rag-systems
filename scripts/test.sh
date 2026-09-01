#!/usr/bin/env bash
# ===== RAG Systems Test Script (Linux/Mac) =====
# Usage: ./test.sh [options] [module]
#   ./test.sh                     - Run all tests
#   ./test.sh --coverage          - Run tests with coverage (JaCoCo)
#   ./test.sh --architecture      - Run only architecture tests
#   ./test.sh rag-basic           - Run only a specific module
#   ./test.sh rag-basic --coverage - Combine module + coverage

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

ARCHITECTURE_FLAG=""
COVERAGE_GOAL="test"
MODULE_SPEC=""

for arg in "$@"; do
    case "$arg" in
        --coverage) COVERAGE_GOAL="verify" ;;
        --architecture) ARCHITECTURE_FLAG="-Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false" ;;
        install) mvn install -DskipTests >/dev/null 2>&1 ;;
        *) MODULE_SPEC="-pl apps/$arg -am" ;;
    esac
done

if [ -z "$MODULE_SPEC" ]; then
    echo "Running tests for all modules (goal: $COVERAGE_GOAL)..."
    mvn "$COVERAGE_GOAL" $ARCHITECTURE_FLAG
else
    echo "Running tests for $MODULE_SPEC (goal: $COVERAGE_GOAL)..."
    mvn $MODULE_SPEC "$COVERAGE_GOAL" $ARCHITECTURE_FLAG
fi

echo
echo "Test complete."
