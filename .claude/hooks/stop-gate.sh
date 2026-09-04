#!/usr/bin/env bash
# ===== RAG Systems Stop-gate hook (Linux/Mac) =====
# Enforces the SDLC rule "always execute all tests after implementation".
# Runs on the Stop event. Reads the hook JSON (optional) from stdin and
# inspects the working tree. Emits an instructive stopReason when JAVA code
# was modified on a feature branch, reminding the agent to run tests before done.
#
# Scope: only .java changes trigger the test reminder. Docs/skills/rules/config
# changes (no .java, no build/script/pom changes) skip the test gate per
# docs/process/asdlc.md Rule 1, so they are intentionally silent here.
#
# Returns 0 (continue:true) so normal workflows are never hard-blocked; the
# hard gate lives in `dev auto-merge` (merges only green PRs) and the rules.
set -uo pipefail

ROOT="${1:-$(pwd)}"
cd "$ROOT" || exit 0

BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")"
IS_FEATURE=0
case "$BRANCH" in
    feat/*) IS_FEATURE=1 ;;
esac

MODIFIED_SRC="$(git status --porcelain 2>/dev/null | grep -E '\.java$' | sed 's/^...//' || true)"

if [ "$IS_FEATURE" = "1" ] && [ -n "$MODIFIED_SRC" ]; then
    echo '{"continue":true,"stopReason":"SDLC gate: Java source files were modified on a feature branch. Before finishing, run the full test suite: scripts/test.sh (Unix) or scripts/test.bat (Windows), then `dev check [module]` (tests + SonarQube). After tests pass, commit and push: dev commit \"message\" then dev push. Do not open a PR or merge until the tests pass. (Docs/skills/rules/config-only changes skip this gate per asdlc.md Rule 1.)"}'
else
    echo '{"continue":true}'
fi

exit 0
