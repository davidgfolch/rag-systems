#!/usr/bin/env bash
# ===== Bootstrap env: copy scripts/.env.secrets.example -> root .env.secrets + generate passwords (Linux/Mac) =====
# Idempotent: never overwrites an existing root .env.secrets file; only fills blank passwords.
# Called automatically by docker.sh / run.sh / sonar.sh / install.sh / build.sh / test.sh.

set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

gen_pw() {
    if command -v openssl >/dev/null 2>&1; then
        # SonarQube 26.8+ requires 12+ chars with uppercase, lowercase, and digit
        UPPER=$(openssl rand -hex 1 | tr 'a-f' 'A-F')
        LOWER=$(openssl rand -hex 1)
        DIGIT=$(openssl rand -hex 1 | tr 'a-f' '0-7')
        REST=$(openssl rand -hex 13)
        echo "${UPPER}${LOWER}${DIGIT}${REST}"
    else
        echo "S${RANDOM}a${RANDOM}$(date +%s%N | tail -c 10)"
    fi
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
