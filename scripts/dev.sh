#!/usr/bin/env bash
# ===== RAG Systems Parallel Development Workflow (Linux/Mac) =====
# Usage: ./dev.sh <command> [args]
#   ./dev.sh new <name>       - Clone to ../rag-systems-<name>, branch feat/<name>, copy Sonar token
#   ./dev.sh checkout <br>    - Switch branch (current dir)
#   ./dev.sh test [module]    - Run tests (delegates to test.sh)
#   ./dev.sh sonar [token]    - Run SonarQube scan (delegates to sonar.sh)
#   ./dev.sh check [module]   - Tests, then SonarQube scan if tests pass
#   ./dev.sh commit "msg"     - git add -A && git commit
#   ./dev.sh push             - git push -u origin <branch>
#   ./dev.sh pr [title]       - Create PR against main
#   ./dev.sh auto-merge [-k]  - Wait for green checks, auto-merge, auto-resolve conflicts, delete remote branch + clone (keep clone with -k)
#   ./dev.sh merge [-k]       - Alias of auto-merge
#   ./dev.sh cleanup <name|path> - Delete a sibling clone by name, prefixed name, or path
#   ./dev.sh status           - Branch, dirty state, ahead/behind

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PARENT="$(cd "$ROOT/.." && pwd -P)"
CMD="${1:-}"

curbranch() { git rev-parse --abbrev-ref HEAD; }

validate_clone() {
    local target="$1" base
    [ -d "$target" ] || return 1
    [ "$target" != "$ROOT" ] || return 1
    base="$(basename "$target")"
    case "$base" in rag-systems-*) ;; *) return 1 ;; esac
    [ "$(dirname "$target")" = "$PARENT" ] || return 1
}

resolve_clone() {
    local arg="$1" name candidate
    case "$arg" in
        "~") arg="$HOME" ;;
        "~/"*) arg="$HOME/${arg#\~/}" ;;
    esac
    case "$arg" in
        /*)
            [ -d "$arg" ] || return 1
            candidate="$(cd "$arg" && pwd -P)"
            ;;
        */*)
            if [ -d "$arg" ]; then
                candidate="$(cd "$arg" && pwd -P)"
            elif [ -d "$ROOT/$arg" ]; then
                candidate="$(cd "$ROOT/$arg" && pwd -P)"
            else
                return 1
            fi
            ;;
        *)
            name="${arg#rag-systems-}"
            [ -n "$name" ] || return 1
            [ -d "$PARENT/rag-systems-$name" ] || return 1
            candidate="$(cd "$PARENT/rag-systems-$name" && pwd -P)"
            ;;
    esac
    validate_clone "$candidate" || return 1
    printf '%s\n' "$candidate"
}

list_clones() {
    local dir found=false
    for dir in "$PARENT"/rag-systems-*/; do
        [ -d "$dir" ] || continue
        dir="$(cd "$dir" && pwd -P)"
        if [ "$dir" = "$ROOT" ]; then continue; fi
        echo "  $(basename "$dir")"
        found=true
    done
    [ "$found" = true ] || echo "  (none)"
}

stop_codegraph_daemon() {
    local pf="$1" pid
    [ -f "$pf" ] || return 0
    pid="$(jq -r '.pid // empty' "$pf" 2>/dev/null || sed -n 's/.*"pid": *\([0-9]*\).*/\1/p' "$pf")"
    [ -n "$pid" ] || return 0
    if kill -0 "$pid" 2>/dev/null && ps -o args= -p "$pid" 2>/dev/null | grep -q 'codegraph'; then
        echo "Stopping CodeGraph daemon $pid (tracked by $pf)"
        kill "$pid" 2>/dev/null || true
    else
        echo "Skipping stale daemon record $pf (pid $pid is not a CodeGraph process)."
    fi
}

release_codegraph_locks() {
    local target="$1"
    if [ ! -f "$target/.codegraph/codegraph.db" ]; then
        echo "No CodeGraph index in $target - nothing to release."
        return 0
    fi
    stop_codegraph_daemon "$target/.codegraph/daemon.pid"
    return 0
}

delete_clone() {
    local target="$1"
    release_codegraph_locks "$target"
    cd "$target/.." || return 1
    echo "Deleting clone $target..."
    rm -rf "$target" 2>/dev/null || true
    if [ -e "$target" ]; then
        echo "Delete blocked; releasing main-workspace CodeGraph daemon and retrying..."
        stop_codegraph_daemon "$ROOT/../rag-systems/.codegraph/daemon.pid"
        rm -rf "$target" 2>/dev/null || true
    fi
    if [ -e "$target" ]; then
        echo "Error: could not delete $target even after releasing CodeGraph daemons."
        return 1
    fi
    echo "Clone deleted."
}

case "$CMD" in
    new)
        NAME="${2:-}"
        [ -n "$NAME" ] || { echo "Usage: ./dev.sh new <name>"; exit 1; }
        NAME="$(basename "$NAME")"
        NAME="${NAME#rag-systems-}"
        CLONE="$PARENT/rag-systems-$NAME"
        BRANCH="feat/$NAME"
        if [ -d "$CLONE" ]; then
            echo "Clone already exists: $CLONE"
            echo "Reusing it. Ensure its branch is $BRANCH."
        else
            ORIGIN_URL="$(git remote get-url origin)"
            echo "Cloning $ORIGIN_URL to $CLONE..."
            git clone "$ORIGIN_URL" "$CLONE" || { echo "Error: clone failed."; exit 1; }
        fi
        cd "$CLONE"
        git fetch origin >/dev/null 2>&1 || true
        git checkout -b "$BRANCH" origin/main 2>/dev/null || git checkout "$BRANCH" 2>/dev/null || { echo "Error: could not set branch $BRANCH."; exit 1; }
        # Copy SONAR_TOKEN (and admin pw) from the original workspace into this clone
        if [ -f "$ROOT/.env.secrets" ]; then
            if [ ! -f ".env.secrets" ]; then cp "$ROOT/.env.secrets" ".env.secrets"; fi
            echo "Sonar token propagated into .env.secrets"
        else
            echo "Note: no .env.secrets in the original. SonarQube token will be auto-generated on first scan."
        fi
        if command -v codegraph >/dev/null 2>&1; then
            echo "Building CodeGraph index in $CLONE..."
            (cd "$CLONE" && codegraph init -y) || echo "Warning: CodeGraph index build failed for $CLONE."
        else
            echo "CodeGraph CLI not found; skipping clone index build."
        fi
        echo
        echo "Clone ready: $CLONE"
        echo "Branch: $BRANCH"
        ;;
    checkout)
        [ -n "${2:-}" ] || { echo "Usage: ./dev.sh checkout <branch>"; exit 1; }
        git checkout "$2"
        ;;
    test)
        shift
        (cd "$ROOT" && bash scripts/test.sh "$@")
        ;;
    sonar)
        shift
        (cd "$ROOT" && bash scripts/sonar.sh "$@")
        ;;
    check)
        shift
        (cd "$ROOT" && bash scripts/test.sh "$@")
        TOKEN="${SONAR_TOKEN:-}"
        if [ -z "$TOKEN" ] && [ -f "$ROOT/.env.secrets" ]; then
            TOKEN=$(grep '^SONAR_TOKEN=' "$ROOT/.env.secrets" | head -1 | cut -d= -f2- || true)
        fi
        if [ -z "$TOKEN" ]; then
            echo "No SonarQube token found; bootstrapping and scanning via up-scan..."
            (cd "$ROOT" && bash scripts/sonar.sh up-scan)
        else
            (cd "$ROOT" && bash scripts/sonar.sh scan "$TOKEN")
        fi
        ;;
    commit)
        [ -n "${2:-}" ] || { echo "Usage: ./dev.sh commit \"message\""; exit 1; }
        git add -A
        git commit -m "$2"
        ;;
    push)
        BR="$(curbranch)"
        if [ "$BR" = "main" ]; then echo "Refusing to push main directly."; exit 1; fi
        git push -u origin "$BR"
        ;;
    pr)
        BR="$(curbranch)"
        TITLE="${2:-}"
        [ -n "$TITLE" ] || TITLE="$(git log -1 --pretty=%s)"
        if [ "$BR" = "main" ]; then echo "Refusing to open a PR from main."; exit 1; fi
        gh pr create --base main --head "$BR" --title "$TITLE" --body "Automated PR from the dev workflow for branch $BR."
        ;;
    merge|auto-merge)
        KEEP="${2:-}"
        BR="$(curbranch)"
        if [ "$BR" = "main" ]; then echo "Cannot merge from main. Run inside a feature clone."; exit 1; fi
        if ! command -v gh >/dev/null 2>&1; then echo "Error: 'gh' CLI is required. Install GitHub CLI and authenticate."; exit 1; fi
        PRNUM="$(gh pr list --head "$BR" --json number --jq '.[0].number' || true)"
        if [ -z "$PRNUM" ]; then echo "Error: no open PR found for branch $BR."; exit 1; fi

        echo "Resolving PR #$PRNUM state..."
        echo "Waiting for PR checks to appear..."
        CHECKS=0
        for _ in $(seq 1 30); do
            CHECKS="$(gh pr view "$PRNUM" --json statusCheckRollup --jq '.statusCheckRollup | length' 2>/dev/null || echo 0)"
            [ "${CHECKS:-0}" -gt 0 ] && break
            sleep 10
        done
        if [ "${CHECKS:-0}" -eq 0 ]; then
            echo "No checks reported for PR #$PRNUM. Clone kept for inspection."
            exit 1
        fi

        echo "Waiting for PR checks to be green..."
        if ! timeout 900 gh pr checks "$PRNUM" --watch; then
            echo "PR checks did not all pass within timeout. Clone kept for inspection."
            exit 1
        fi

        BAD_CHECKS="$(gh pr view "$PRNUM" --json statusCheckRollup --jq '[.statusCheckRollup[]? | select(.conclusion == "FAILURE" or .conclusion == "CANCELLED" or .conclusion == "TIMED_OUT" or .conclusion == "ACTION_REQUIRED" or .state == "ERROR") | .name] | join(", ")')"
        if [ -n "$BAD_CHECKS" ]; then
            echo "PR has failing or cancelled checks: $BAD_CHECKS. Clone kept for inspection."
            exit 1
        fi

        if ! gh pr view "$PRNUM" --json mergeable --jq '.mergeable' | grep -q 'MERGEABLE'; then
            echo "PR is not mergeable yet; attempting to auto-resolve conflicts..."
            git fetch origin main >/dev/null 2>&1
            if git rebase origin/main; then
                echo "Rebase succeeded; conflict resolved automatically."
                git push --force-with-lease origin "$BR" || { echo "Push after rebase failed. Clone kept."; exit 1; }
            else
                git rebase --abort >/dev/null 2>&1
                echo "Error: could not auto-resolve conflicts (rebase failed). Resolve manually, then re-run auto-merge. Clone kept."
                exit 1
            fi
        fi

        echo "Merging PR #$PRNUM (auto-merge once required checks pass)..."
        if gh pr merge "$PRNUM" --auto --squash --delete-branch; then
            echo "Auto-merge enabled; waiting for the merge..."
            STATE=""
            for _ in $(seq 1 90); do
                STATE="$(gh pr view "$PRNUM" --json state --jq '.state')"
                [ "$STATE" = "MERGED" ] && break
                if [ "$STATE" = "CLOSED" ]; then
                    echo "PR closed without merging. Clone kept for inspection."
                    exit 1
                fi
                sleep 10
            done
            if [ "$STATE" != "MERGED" ]; then
                echo "Timed out waiting for the merge. Clone kept for inspection."
                exit 1
            fi
            echo "PR merged."
            if [ "$KEEP" = "-k" ]; then
                echo "Keeping clone."
            else
                SUFFIX="${BR#feat/}"
                if TARGET="$(resolve_clone "$SUFFIX")"; then
                    delete_clone "$TARGET"
                fi
            fi
        else
            echo "PR merge failed. Clone kept for inspection."
        fi
        ;;
    cleanup)
        NAME="${2:-}"
        [ -n "$NAME" ] || { echo "Usage: ./dev.sh cleanup <name|path>"; exit 1; }
        if TARGET="$(resolve_clone "$NAME")"; then
            delete_clone "$TARGET"
        else
            echo "Nothing to clean: '$NAME' does not resolve to an existing clone under $PARENT."
            echo "Already deleted? Discovered clones:"
            list_clones
        fi
        ;;
    status)
        echo "Branch: $(curbranch)"
        echo
        git status --short
        echo
        git rev-list --left-right --count origin/main...HEAD
        ;;
    *)
        echo "Usage: ./dev.sh <command> [args]"
        echo "  new <name>  checkout <br>  test [module]  sonar [token]"
        echo "  check [module]  commit \"msg\"  push  pr [title]"
        echo "  auto-merge [-k]  merge [-k]  cleanup <name|path>  status"
        exit 1
        ;;
esac
