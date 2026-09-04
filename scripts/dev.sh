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
#   ./dev.sh auto-merge [-k]  - Wait for green checks, auto-merge, auto-resolve conflicts, delete clone (keep with -k)
#   ./dev.sh merge [-k]       - Alias of auto-merge
#   ./dev.sh cleanup <name>   - Delete ../rag-systems-<name>
#   ./dev.sh status           - Branch, dirty state, ahead/behind

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CMD="${1:-}"

curbranch() { git rev-parse --abbrev-ref HEAD; }

case "$CMD" in
    new)
        NAME="${2:-}"
        [ -n "$NAME" ] || { echo "Usage: ./dev.sh new <name>"; exit 1; }
        PARENT="$(cd "$ROOT/.." && pwd)"
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
        echo "Waiting for PR checks to be green..."
        if ! timeout 900 gh pr checks "$PRNUM" --watch; then
            echo "PR checks did not all pass within timeout. Clone kept for inspection."
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

        echo "Merging PR #$PRNUM..."
        if gh pr merge "$PRNUM" --squash; then
            echo "PR merged."
            if [ "$KEEP" = "-k" ]; then
                echo "Keeping clone."
            else
                SUFFIX="${BR#feat/}"
                TARGET="$ROOT/../rag-systems-$SUFFIX"
                if [ -d "$TARGET" ]; then
                    cd "$TARGET/.."
                    echo "Deleting clone $TARGET..."
                    rm -rf "$TARGET"
                    echo "Clone deleted."
                fi
            fi
        else
            echo "PR merge failed. Clone kept for inspection."
        fi
        ;;
    cleanup)
        NAME="${2:-}"
        [ -n "$NAME" ] || { echo "Usage: ./dev.sh cleanup <name>"; exit 1; }
        TARGET="$ROOT/../rag-systems-$NAME"
        if [ -d "$TARGET" ]; then
            echo "Deleting $TARGET..."
            rm -rf "$TARGET"
            echo "Deleted."
        else
            echo "Not found: $TARGET"
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
        echo "  auto-merge [-k]  merge [-k]  cleanup <name>  status"
        exit 1
        ;;
esac
