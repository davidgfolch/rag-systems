#!/usr/bin/env bash
# ===== RAG Systems Test Script (Linux/Mac) =====
# Usage: ./test.sh [options] [module]
#   ./test.sh                     - Run all tests with coverage (verify; JaCoCo 90% check enforced)
#   ./test.sh --coverage          - Alias of the default (verify + JaCoCo check)
#   ./test.sh rag-basic           - Run only a specific module (verify)
#   ./test.sh rag-basic --coverage - Combine module + verify
#   ./test.sh --list              - List valid modules and exit
# <module> accepts a bare name (rag-basic) or a path form (apps/rag-basic).
# Architecture tests (ArchitectureTest) always run as part of the test suite.

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

COVERAGE_GOAL="verify"
MODULE_SPEC=""
LIST_ONLY=false
DO_INSTALL=false
MODULE_ARGS=()

for arg in "$@"; do
    case "$arg" in
        --coverage) COVERAGE_GOAL="verify" ;;
        --list) LIST_ONLY=true ;;
        install) DO_INSTALL=true ;;
        -*) echo "Unknown option: $arg" >&2; exit 2 ;;
        *) MODULE_ARGS+=("$arg") ;;
    esac
done

if [ "$LIST_ONLY" = true ]; then
    echo "Valid modules:"
    bash "$ROOT/scripts/modules.sh" list
    exit 0
fi

# Bootstrap .env.secrets from scripts/.env.secrets.example (idempotent)
bash scripts/bootstrap-env.sh

if [ "$DO_INSTALL" = true ]; then
    mvn install -DskipTests -Djacoco.skip=true >/dev/null 2>&1
fi

if [ ${#MODULE_ARGS[@]} -gt 0 ]; then
    for arg in "${MODULE_ARGS[@]}"; do
        MODULE="${arg#apps/}"
        MODULE="${MODULE%/}"
        if ! bash "$ROOT/scripts/modules.sh" check "$MODULE"; then
            echo "Unknown module: $MODULE" >&2
            echo "Valid modules:" >&2
            bash "$ROOT/scripts/modules.sh" list | sed 's/^/  /' >&2
            echo "Hint: ./test.sh --list lists them." >&2
            exit 1
        fi
        MODULE_SPEC="-pl apps/$MODULE -am"
    done
fi

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
