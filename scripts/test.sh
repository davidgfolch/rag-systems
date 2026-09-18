#!/usr/bin/env bash
# ===== RAG Systems Test Script (Linux/Mac) =====
# Usage: ./test.sh [options] [module]
#   ./test.sh                     - Run all tests with coverage (verify; JaCoCo 90% check enforced)
#   ./test.sh --coverage          - Alias of the default (verify + JaCoCo check)
#   ./test.sh rag-basic           - Run only a specific module (verify)
#   ./test.sh rag-basic --coverage - Combine module + verify
# Architecture tests (ArchitectureTest) always run as part of the test suite.

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Bootstrap root .env files from scripts/.env*.example (idempotent)
bash scripts/bootstrap-env.sh

COVERAGE_GOAL="verify"
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

# If coverage was run, regenerate badges
if [ "$COVERAGE_GOAL" = "verify" ]; then
    if [ -x "$ROOT/scripts/coverage-badges.sh" ]; then
        bash "$ROOT/scripts/coverage-badges.sh"
    fi
fi

echo
echo "Test complete."
