#!/usr/bin/env bash
# ===== Bootstrap env: copy scripts/.env.*.example -> root + generate passwords (Linux/Mac) =====
# Idempotent: never overwrites an existing root .env file; only fills blank passwords.
# Called automatically by docker.sh / run.sh / sonar.sh / install.sh / build.sh / test.sh.

set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

gen_pw() {
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -hex 16
    else
        echo "$RANDOM$RANDOM$RANDOM-$(date +%s%N)"
    fi
}

# Copy each scripts/.env*.example to the matching root file if it does not exist.
for example in scripts/.env*.example; do
    [ -e "$example" ] || continue
    base="${example##*/}"                 # e.g. .env.example  /  .env.secrets.example
    name="${base%.example}"               # e.g. .env         /  .env.secrets
    target="$ROOT/$name"
    if [ ! -f "$target" ]; then
        cp "$example" "$target"
        echo "Created $name from scripts/$base"
    fi
done

# Fill blank PGVECTOR_PASSWORD in .env
if [ -f "$ROOT/.env" ] && grep -q "^PGVECTOR_PASSWORD=$" "$ROOT/.env"; then
    PASSWORD=$(gen_pw)
    sed -i.bak "s|^PGVECTOR_PASSWORD=$|PGVECTOR_PASSWORD=$PASSWORD|" "$ROOT/.env"
    rm -f "$ROOT/.env.bak"
    echo "Generated PGVECTOR_PASSWORD in .env"
fi

# Fill blank SONAR_ADMIN_PASSWORD in .env.secrets
if [ -f "$ROOT/.env.secrets" ] && grep -q "^SONAR_ADMIN_PASSWORD=$" "$ROOT/.env.secrets"; then
    PASSWORD=$(gen_pw)
    sed -i.bak "s|^SONAR_ADMIN_PASSWORD=$|SONAR_ADMIN_PASSWORD=$PASSWORD|" "$ROOT/.env.secrets"
    rm -f "$ROOT/.env.secrets.bak"
    echo "Generated SONAR_ADMIN_PASSWORD in .env.secrets"
fi
