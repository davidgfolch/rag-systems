#!/usr/bin/env bash
# ===== RAG Systems Coverage Badges Generator (Linux/Mac) =====
# Usage: ./coverage-badges.sh
#   Generates per-module coverage badges from JaCoCo CSV reports
#   and inserts them into README.md between markers.
# Requires: bash, grep, sed, awk

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
README="$ROOT/README.md"
START_MARKER="<!-- COVERAGE_BADGES_START -->"
END_MARKER="<!-- COVERAGE_BADGES_END -->"

echo "Generating per-module coverage badges..."

# Find all modules with JaCoCo CSV reports
declare -a MODULES=()
declare -a COVERAGES=()
declare -a COLORS=()

# Map of module directories we expect in the monorepo
EXPECTED_MODULES=(
    "rag-common"
    "rag-basic"
    "rag-memory"
    "rag-webcrawler"
    "rag-advanced"
    "rag-agentic"
    "rag-evaluation"
    "rag-observability"
    "rag-cli"
    "rag-tui"
)

for MODULE in "${EXPECTED_MODULES[@]}"; do
    CSV="$ROOT/apps/$MODULE/target/site/jacoco/jacoco.csv"

    if [ ! -f "$CSV" ]; then
        echo "  Skipping $MODULE: no JaCoCo CSV report at $CSV"
        continue
    fi

    # Sum INSTRUCTION_MISSED and INSTRUCTION_COVERED across all classes
    # JaCoCo CSV columns (0-indexed): 0=GROUP, 1=PACKAGE, 2=CLASS, 3=INSTRUCTION_MISSED, 4=INSTRUCTION_COVERED, ...
    # Sum across all rows (skip header line 1)
    MISSED_TOTAL=$(tail -n +2 "$CSV" | awk -F',' '{sum += $4} END {print sum+0}')
    COVERED_TOTAL=$(tail -n +2 "$CSV" | awk -F',' '{sum += $5} END {print sum+0}')

    if [ "$MISSED_TOTAL" -eq 0 ] && [ "$COVERED_TOTAL" -eq 0 ]; then
        echo "  Skipping $MODULE: zero instruction data"
        continue
    fi

    if [ "$MISSED_TOTAL" -eq 0 ]; then
        PCT=100
    else
        PCT=$(awk "BEGIN {printf \"%.0f\", ($COVERED_TOTAL / ($COVERED_TOTAL + $MISSED_TOTAL)) * 100}")
    fi

    # Determine badge color based on coverage % (matching JaCoCo thresholds)
    if [ "$PCT" -ge 85 ]; then
        COLOR="brightgreen"
    elif [ "$PCT" -ge 70 ]; then
        COLOR="yellowgreen"
    elif [ "$PCT" -ge 50 ]; then
        COLOR="yellow"
    else
        COLOR="red"
    fi

    MODULES+=("$MODULE")
    COVERAGES+=("$PCT")
    COLORS+=("$COLOR")

    echo "  $MODULE: $PCT% coverage (color: $COLOR)"
done

# If no modules found, exit early
if [ ${#MODULES[@]} -eq 0 ]; then
    echo "No modules with coverage data found; skipping README update."
    exit 0
fi

# Build the badge markdown
BADGE_LINES=()
for i in "${!MODULES[@]}"; do
    MOD="${MODULES[$i]}"
    PCT="${COVERAGES[$i]}"
    COL="${COLORS[$i]}"
    # shields.io static badge URL: https://img.shields.io/badge/<label>-<message>-<color>
    # Encode hyphens in label as %2D for shields.io
    ENCODED=$(echo "$MOD" | sed 's/-/--/g')
    BADGE_URL="https://img.shields.io/badge/${ENCODED}-${PCT}%25-${COL}"
    BADGE_LINES+=("![${MOD}](${BADGE_URL})")
done

# Build badge row - inline badges separated by spaces on one line
BADGE_ROW=$(printf '%s' "${BADGE_LINES[0]}")
for i in $(seq 1 $((${#MODULES[@]} - 1))); do
    BADGE_ROW="${BADGE_ROW}  ${BADGE_LINES[$i]}"
done

# Build the markdown block
BLOCK="\n${START_MARKER}\n${BADGE_ROW}\n${END_MARKER}\n"

# Read current README and insert/Replace between markers
if [ ! -f "$README" ]; then
    echo "Error: $README not found"
    exit 1
fi

# Find marker line numbers
START_LINE=$(grep -n "$START_MARKER" "$README" | head -1 | cut -d: -f1) || true
END_LINE=$(grep -n "$END_MARKER" "$README" | head -1 | cut -d: -f1) || true

if [ -z "$START_LINE" ] || [ -z "$END_LINE" ]; then
    # Markers not found - we need to add them
    # Insert after line 6 (after the last existing badge: Maven)
    CI_BADGE_LINE=$(grep -n '!\[CI\]' "$README" | head -1 | cut -d: -f1) || true
    if [ -n "$CI_BADGE_LINE" ]; then
        # Insert after the CI badge line
        TEMP_FILE=$(mktemp)
        head -n "$CI_BADGE_LINE" "$README" > "$TEMP_FILE"
        printf '\n' >> "$TEMP_FILE"
        printf '%s\n' "$START_MARKER" >> "$TEMP_FILE"
        printf '%s\n' "$BADGE_ROW" >> "$TEMP_FILE"
        printf '%s\n' "$END_MARKER" >> "$TEMP_FILE"
        tail -n +"$(($CI_BADGE_LINE + 1))" "$README" >> "$TEMP_FILE"
        mv "$TEMP_FILE" "$README"
    else
        # Fallback: insert after line 6
        TEMP_FILE=$(mktemp)
        head -n 6 "$README" > "$TEMP_FILE"
        printf '\n' >> "$TEMP_FILE"
        printf '%s\n' "$START_MARKER" >> "$TEMP_FILE"
        printf '%s\n' "$BADGE_ROW" >> "$TEMP_FILE"
        printf '%s\n' "$END_MARKER" >> "$TEMP_FILE"
        tail -n +"7" "$README" >> "$TEMP_FILE"
        mv "$TEMP_FILE" "$README"
    fi
    echo "Inserted coverage badges markers into README.md"
else
    # Markers exist - replace content between them (keep markers)
    TEMP_FILE=$(mktemp)
    head -n "$((START_LINE - 1))" "$README" > "$TEMP_FILE"
    printf '%s\n' "$START_MARKER" >> "$TEMP_FILE"
    printf '%s\n' "$BADGE_ROW" >> "$TEMP_FILE"
    printf '%s\n' "$END_MARKER" >> "$TEMP_FILE"
    tail -n "+$((END_LINE + 1))" "$README" >> "$TEMP_FILE"
    mv "$TEMP_FILE" "$README"
    echo "Updated coverage badges in README.md"
fi

echo "Done. Coverage badges generated for ${#MODULES[@]} module(s)."