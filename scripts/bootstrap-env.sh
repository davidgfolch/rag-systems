#!/usr/bin/env bash
# ===== Bootstrap env: copy scripts/.env.secrets.example -> root .env.secrets + generate passwords (Linux/Mac) =====
# Idempotent: never overwrites an existing root .env.secrets file; only fills blank passwords.
# Called automatically by docker.sh / run.sh / sonar.sh / install.sh / build.sh / test.sh.

set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

gen_pw() {
    # SonarQube 26.8+ requires 12+ chars with uppercase, lowercase, digit, and
    # a special character. Use only chars that are safe for .env, sed, and batch.
    local upper='ABCDEFGHIJKLMNOPQRSTUVWXYZ'
    local lower='abcdefghijklmnopqrstuvwxyz'
    local digit='0123456789'
    local special='-_.@'
    local all="${upper}${lower}${digit}${special}"
    local pw="${upper:RANDOM%${#upper}:1}${lower:RANDOM%${#lower}:1}${digit:RANDOM%${#digit}:1}${special:RANDOM%${#special}:1}"
    local i
    for i in $(seq 1 28); do
        pw+="${all:RANDOM%${#all}:1}"
    done
    printf '%s\n' "$pw"
}

# Copy scripts/.env.secrets.example to the root .env.secrets if it does not exist.
if [ -f "$ROOT/scripts/.env.secrets.example" ] && [ ! -f "$ROOT/.env.secrets" ]; then
    cp "$ROOT/scripts/.env.secrets.example" "$ROOT/.env.secrets"
    echo "Created .env.secrets from scripts/.env.secrets.example"
fi

# Fill blank PGVECTOR_PASSWORD in .env.secrets
if [ -f "$ROOT/.env.secrets" ] && grep -q "^PGVECTOR_PASSWORD=$" "$ROOT/.env.secrets"; then
    PASSWORD=$(gen_pw)
    sed -i.bak "s|^PGVECTOR_PASSWORD=$|PGVECTOR_PASSWORD=$PASSWORD|" "$ROOT/.env.secrets"
    rm -f "$ROOT/.env.secrets.bak"
    echo "Generated PGVECTOR_PASSWORD in .env.secrets"
fi

# Fill blank SONAR_ADMIN_PASSWORD in .env.secrets
if [ -f "$ROOT/.env.secrets" ] && grep -q "^SONAR_ADMIN_PASSWORD=$" "$ROOT/.env.secrets"; then
    PASSWORD=$(gen_pw)
    sed -i.bak "s|^SONAR_ADMIN_PASSWORD=$|SONAR_ADMIN_PASSWORD=$PASSWORD|" "$ROOT/.env.secrets"
    rm -f "$ROOT/.env.secrets.bak"
    echo "Generated SONAR_ADMIN_PASSWORD in .env.secrets"
fi
