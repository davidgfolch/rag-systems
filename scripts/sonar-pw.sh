#!/usr/bin/env bash
# ===== SonarQube Bootstrap: set admin password + generate token (Linux/Mac) =====
# Called automatically by sonar.sh after SonarQube is ready.
# Usage: sonar-pw.sh [--gate-only]
#   --gate-only  Only (re)apply the quality gate; skip password/token bootstrap.
# Idempotent: uses .sonarqube/admin_pw_set marker to skip admin/token setup on
# subsequent runs, but always (re)applies the quality gate.
# Env: SONAR_ADMIN_PASSWORD (from .env.secrets or shell), SONAR_TOKEN (from .env.secrets or shell).

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

HOST="http://localhost:9000"
MARKER=".sonarqube/admin_pw_set"
SECRETS_FILE=".env.secrets"
TOKEN_NAME="rag-local-ci"
ADMIN_USER="admin"
GATE_NAME="RAG 90% Coverage"
GATE_CONDITION_METRIC="coverage"
GATE_CONDITION_OP="LT"
GATE_CONDITION_VALUE="90"
PROJECT_KEY="com.rag:rag-systems"
GATE_ONLY=0
if [ "${1:-}" = "--gate-only" ]; then GATE_ONLY=1; fi

# Load .env.secrets if present (SONAR_ADMIN_PASSWORD, SONAR_TOKEN)
if [ -f "$SECRETS_FILE" ]; then
    set -a
    # shellcheck disable=SC1090
    source "$SECRETS_FILE"
    set +a
fi

NEW_PW="${SONAR_ADMIN_PASSWORD:-admin}"

# SonarQube requires 12+ chars with uppercase, lowercase, digit and special char.
strengthen_password() {
    local pw="$1"
    [[ "$pw" =~ [A-Z] ]] || pw+="A"
    [[ "$pw" =~ [a-z] ]] || pw+="a"
    [[ "$pw" =~ [0-9] ]] || pw+="1"
    [[ "$pw" =~ [^A-Za-z0-9] ]] || pw+="-"
    while [ "${#pw}" -lt 12 ]; do pw+="x9"; done
    printf '%s\n' "$pw"
}

persist_password() {
    local pw="$1" escaped
    [ -f "$SECRETS_FILE" ] || return 0
    escaped="${pw//\\/\\\\}"
    escaped="${escaped//&/\\&}"
    escaped="${escaped//|/\\|}"
    if grep -q '^SONAR_ADMIN_PASSWORD=' "$SECRETS_FILE"; then
        sed -i.bak "s|^SONAR_ADMIN_PASSWORD=.*|SONAR_ADMIN_PASSWORD=$escaped|" "$SECRETS_FILE"
        rm -f "$SECRETS_FILE.bak"
    else
        printf 'SONAR_ADMIN_PASSWORD=%s\n' "$pw" >> "$SECRETS_FILE"
    fi
}

STRONG_PW="$(strengthen_password "$NEW_PW")"
if [ "$STRONG_PW" != "$NEW_PW" ]; then
    echo "Strengthening SONAR_ADMIN_PASSWORD to satisfy the SonarQube policy."
    NEW_PW="$STRONG_PW"
    persist_password "$NEW_PW"
fi

admin_auth_ok() {
    local pw="$1" valid
    valid=$(curl -s -u "$ADMIN_USER:$pw" "$HOST/api/authentication/validate" 2>/dev/null || true)
    printf '%s' "$valid" | grep -Eq '"valid"[[:space:]]*:[[:space:]]*true'
}

gate_exists() {
    curl -s -u "$ADMIN_USER:$NEW_PW" "$HOST/api/qualitygates/list" 2>/dev/null \
        | grep -Fq "\"name\":\"$GATE_NAME\""
}

# Query parameters are required by the quality-gate endpoints on SonarQube 26.8;
# sending them as a form body returns HTTP 500. A duplicate name returns 400,
# so the gate is considered ready when it already exists.
configure_qg() {
    local code
    echo "Configuring quality gate (>= $GATE_CONDITION_VALUE% coverage)..."
    code=$(curl -s -o /dev/null -w "%{http_code}" -u "$ADMIN_USER:$NEW_PW" -X POST \
        "$HOST/api/qualitygates/create" -G --data-urlencode "name=$GATE_NAME" || true)

    if [ "$code" = "200" ] || gate_exists; then
        curl -s -o /dev/null -u "$ADMIN_USER:$NEW_PW" -X POST \
            "$HOST/api/qualitygates/create_condition" -G \
            --data-urlencode "gateName=$GATE_NAME" \
            --data-urlencode "metric=$GATE_CONDITION_METRIC" \
            --data-urlencode "op=$GATE_CONDITION_OP" \
            --data-urlencode "error=$GATE_CONDITION_VALUE" || true
        curl -s -o /dev/null -u "$ADMIN_USER:$NEW_PW" -X POST \
            "$HOST/api/qualitygates/select" -G \
            --data-urlencode "gateName=$GATE_NAME" \
            --data-urlencode "projectKey=$PROJECT_KEY" || true
        echo "Quality gate '$GATE_NAME' configured (>= $GATE_CONDITION_VALUE% coverage)."
    else
        echo "Warning: could not create quality gate (HTTP $code)." >&2
    fi

    if [ "$GATE_ONLY" = "0" ]; then
        mkdir -p "$(dirname "$MARKER")"
        touch "$MARKER"
    fi
}

if [ "$GATE_ONLY" = "1" ]; then
    configure_qg
    exit 0
fi

if [ -f "$MARKER" ]; then
    echo "SonarQube admin password already configured."
    configure_qg
    exit 0
fi

echo "Waiting for SonarQube to be ready..."
STATUS=""
for i in $(seq 1 30); do
    STATUS=$(curl -s "$HOST/api/system/status" 2>/dev/null | grep -o '"status":"[^"]*"' | cut -d'"' -f4 || true)
    if [ "$STATUS" = "UP" ]; then
        break
    fi
    sleep 5
done

if [ "$STATUS" != "UP" ]; then
    echo "Error: SonarQube did not become ready within 150s." >&2
    exit 1
fi

if admin_auth_ok "$NEW_PW"; then
    echo "Admin password already set to SONAR_ADMIN_PASSWORD."
else
    echo "Setting admin password..."
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u "$ADMIN_USER:admin" -X POST \
        "$HOST/api/users/change_password" \
        --data-urlencode "login=$ADMIN_USER" \
        --data-urlencode "previousPassword=admin" \
        --data-urlencode "password=$NEW_PW" || true)
    if [ "$HTTP_CODE" != "200" ] && ! admin_auth_ok "$NEW_PW"; then
        echo "Error: could not set admin password (HTTP $HTTP_CODE)." >&2
        echo "Set SONAR_ADMIN_PASSWORD in $SECRETS_FILE to the current admin password and retry." >&2
        exit 1
    fi
    echo "Admin password set."
fi

echo "Generating analysis token..."
generate_token() {
    curl -s -u "$ADMIN_USER:$NEW_PW" -X POST "$HOST/api/user_tokens/generate" \
        --data-urlencode "name=$TOKEN_NAME" 2>/dev/null || true
}

TOKEN_RESPONSE="$(generate_token)"
TOKEN_VALUE=$(printf '%s' "$TOKEN_RESPONSE" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

if [ -z "$TOKEN_VALUE" ]; then
    curl -s -o /dev/null -u "$ADMIN_USER:$NEW_PW" -X POST "$HOST/api/user_tokens/revoke" \
        --data-urlencode "name=$TOKEN_NAME" 2>/dev/null || true
    TOKEN_RESPONSE="$(generate_token)"
    TOKEN_VALUE=$(printf '%s' "$TOKEN_RESPONSE" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
fi

if [ -z "$TOKEN_VALUE" ]; then
    echo "Warning: could not generate token." >&2
    echo "Check SonarQube logs or generate manually at $HOST" >&2
    configure_qg
    exit 0
fi

echo "Token generated: $TOKEN_VALUE"

# Save token to .env.secrets (update the existing line, or append it).
if [ -f "$SECRETS_FILE" ]; then
    if grep -q "^SONAR_TOKEN=" "$SECRETS_FILE"; then
        sed -i.bak "s|^SONAR_TOKEN=.*|SONAR_TOKEN=$TOKEN_VALUE|" "$SECRETS_FILE"
        rm -f "$SECRETS_FILE.bak"
    else
        printf 'SONAR_TOKEN=%s\n' "$TOKEN_VALUE" >> "$SECRETS_FILE"
    fi
else
    printf 'SONAR_ADMIN_PASSWORD=%s\nSONAR_TOKEN=%s\n' "$NEW_PW" "$TOKEN_VALUE" > "$SECRETS_FILE"
fi

configure_qg

echo "SonarQube bootstrap complete. Token saved to $SECRETS_FILE."
