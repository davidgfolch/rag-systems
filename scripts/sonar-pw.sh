#!/usr/bin/env bash
# ===== SonarQube Bootstrap: set admin password + generate token (Linux/Mac) =====
# Called automatically by sonar.sh after SonarQube is ready.
# Idempotent: uses .sonarqube/admin_pw_set marker to skip on subsequent runs.
# Env: SONAR_ADMIN_PASSWORD (from .env.secrets or shell), SONAR_TOKEN (from .env.secrets or shell).

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

HOST="http://localhost:9000"
MARKER=".sonarqube/admin_pw_set"
SECRETS_FILE=".env.secrets"
TOKEN_NAME="rag-local-ci"

# Load .env.secrets if present (SONAR_ADMIN_PASSWORD, SONAR_TOKEN)
if [ -f "$SECRETS_FILE" ]; then
    set -a
    # shellcheck disable=SC1090
    source "$SECRETS_FILE"
    set +a
fi

NEW_PW="${SONAR_ADMIN_PASSWORD:-admin}"
ADMIN_USER="admin"
ADMIN_PASS="${NEW_PW}"

if [ -f "$MARKER" ]; then
    echo "SonarQube admin password already configured."
    exit 0
fi

echo "Waiting for SonarQube to be ready..."
for i in $(seq 1 30); do
    STATUS=$(curl -sf "$HOST/api/system/status" 2>/dev/null | grep -o '"status":"[^"]*"' | cut -d'"' -f4 || true)
    if [ "$STATUS" = "UP" ]; then
        break
    fi
    sleep 5
done

if [ "$STATUS" != "UP" ]; then
    echo "Error: SonarQube did not become ready within 150s."
    exit 1
fi

echo "Setting admin password..."
HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" \
    -u "$ADMIN_USER:admin" \
    -X POST "$HOST/api/authentication/change_password" \
    -d "login=$ADMIN_USER" \
    -d "previousPassword=admin" \
    -d "password=$NEW_PW")

if [ "$HTTP_CODE" -ne 200 ]; then
    echo "Warning: could not set admin password (HTTP $HTTP_CODE)."
    echo "The server may have already been bootstrapped."
    exit 0
fi

echo "Admin password set."

echo "Generating analysis token..."
TOKEN_RESPONSE=$(curl -sf \
    -u "$ADMIN_USER:$NEW_PW" \
    -X POST "$HOST/api/user_tokens/generate" \
    -d "name=$TOKEN_NAME" 2>/dev/null || true)

TOKEN_VALUE=$(echo "$TOKEN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4 || true)

if [ -z "$TOKEN_VALUE" ]; then
    echo "Warning: could not generate token."
    echo "Check SonarQube logs or generate manually at $HOST"
    exit 0
fi

echo "Token generated: $TOKEN_VALUE"

# Save token to .env.secrets (create or update SONAR_TOKEN line)
if [ -f "$SECRETS_FILE" ]; then
    if grep -q "^SONAR_TOKEN=" "$SECRETS_FILE"; then
        sed -i.bak "s|^SONAR_TOKEN=.*|SONAR_TOKEN=$TOKEN_VALUE|" "$SECRETS_FILE"
        rm -f "$SECRETS_FILE.bak"
    else
        echo "SONAR_TOKEN=$TOKEN_VALUE" >> "$SECRETS_FILE"
    fi
else
    echo "SONAR_ADMIN_PASSWORD=$NEW_PW" > "$SECRETS_FILE"
    echo "SONAR_TOKEN=$TOKEN_VALUE" >> "$SECRETS_FILE"
fi

# Write marker
mkdir -p "$(dirname "$MARKER")"
touch "$MARKER"

echo "SonarQube bootstrap complete. Token saved to $SECRETS_FILE."
