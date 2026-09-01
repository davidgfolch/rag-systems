#!/usr/bin/env bash
# ===== RAG Systems SonarQube Results Exporter (Linux/Mac) =====
# Usage: ./sonar-export.sh [projectKey] [hostUrl]
#   projectKey (default: com.rag:rag-systems)
#   hostUrl    (default: http://localhost:9000)
# Fetches quality gate + key metrics from the SonarQube API and updates the
# "SonarQube -> Latest Results" section of README.md (between the
# <!-- SONARQUBE_RESULTS_START/END --> markers). Requires jq and curl.

set -u
PROJECT="${1:-com.rag:rag-systems}"
HOST="${2:-http://localhost:9000}"
MD_START="<!-- SONARQUBE_RESULTS_START -->"
MD_END="<!-- SONARQUBE_RESULTS_END -->"
METRICS="bugs,vulnerabilities,security_hotspots,code_smells,coverage,duplicated_lines_density"

if ! command -v jq >/dev/null 2>&1; then
    echo "Warning: jq not installed; skipping README update."
    exit 0
fi

probe=$(curl -sf "$HOST/api/measures/component?component=$PROJECT&metricKeys=coverage") || {
    echo "Warning: no analysis found yet on server; skipping README update."
    exit 0
}
gate=$(curl -sf "$HOST/api/qualitygates/project_status?projectKey=$PROJECT") || {
    echo "Warning: could not fetch quality gate; skipping README update."
    exit 0
}
measures=$(curl -sf "$HOST/api/measures/component?component=$PROJECT&metricKeys=$METRICS") || {
    echo "Warning: could not fetch measures; skipping README update."
    exit 0
}

get_metric() {
    printf '%s' "$measures" | jq -r --arg m "$1" '.component.measures[] | select(.metric==$m) | .value'
}

status=$(printf '%s' "$gate" | jq -r '.projectStatus.status')
bugs=$(get_metric bugs)
vulns=$(get_metric vulnerabilities)
hotspots=$(get_metric security_hotspots)
smells=$(get_metric code_smells)
coverage=$(get_metric coverage)
duplication=$(get_metric duplicated_lines_density)
stamp=$(date -u '+%Y-%m-%d %H:%M UTC')

block="| Metric | Value |
|--------|-------|
| Quality Gate | $status |
| Bugs | ${bugs:-n/a} |
| Vulnerabilities | ${vulns:-n/a} |
| Security Hotspots | ${hotspots:-n/a} |
| Code Smells | ${smells:-n/a} |
| Coverage | ${coverage:-n/a}% |
| Duplication | ${duplication:-n/a}%

*Last scan: $stamp*"

start=$(grep -n -- "$MD_START" README.md | head -1 | cut -d: -f1) || true
end=$(grep -n -- "$MD_END" README.md | head -1 | cut -d: -f1) || true
if [ -z "$start" ] || [ -z "$end" ] || [ "$start" -ge "$end" ]; then
    echo "Warning: README.md markers not found; skipping README update."
    exit 0
fi
{
    head -n "$start" README.md
    printf '%s\n' "$block"
    tail -n +"$end" README.md
} > README.md.tmp && mv README.md.tmp README.md
echo "README.md updated with SonarQube results (gate: $status, coverage: ${coverage:-n/a}%)."
exit 0