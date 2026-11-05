#!/usr/bin/env bash
# ===== RAG Systems Module Discovery Helper (Linux/Mac) =====
# Usage: ./modules.sh list [--runnable]
#        ./modules.sh check [--runnable] <module>
#   list            - Print every Maven module under apps/ (directories with pom.xml)
#   list --runnable - Print only modules declaring spring-boot-maven-plugin
#   check <module>  - Exit 0 when <module> is valid, 1 otherwise
# Discovery is filesystem-based, so adding a module under apps/ needs no list update.

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APPS="$ROOT/apps"
RUNNABLE_MARKER="spring-boot-maven-plugin"

has_pom() { [ -f "$APPS/$1/pom.xml" ]; }
is_runnable() { has_pom "$1" && grep -q "$RUNNABLE_MARKER" "$APPS/$1/pom.xml"; }

list_modules() {
    local runnable="$1" dir name
    for dir in "$APPS"/*/; do
        [ -f "$dir/pom.xml" ] || continue
        name="$(basename "$dir")"
        if [ "$runnable" = true ] && ! grep -q "$RUNNABLE_MARKER" "$dir/pom.xml"; then
            continue
        fi
        printf '%s\n' "$name"
    done
}

CMD="${1:-list}"
RUNNABLE=false
MODULE=""
if [ $# -gt 0 ]; then shift; fi
for arg in "$@"; do
    case "$arg" in
        --runnable|runnable) RUNNABLE=true ;;
        *) MODULE="$arg" ;;
    esac
done

case "$CMD" in
    list|--list) list_modules "$RUNNABLE" ;;
    check)
        if [ -z "$MODULE" ]; then
            echo "Usage: modules.sh check [--runnable] <module>" >&2
            exit 2
        fi
        if [ "$RUNNABLE" = true ]; then
            is_runnable "$MODULE"
        else
            has_pom "$MODULE"
        fi
        ;;
    *)
        echo "Usage: modules.sh list [--runnable] | check [--runnable] <module>" >&2
        exit 2
        ;;
esac
