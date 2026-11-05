#!/usr/bin/env bash
# ===== RAG Systems main-branch protection (Linux/Mac) =====
# Applies a repository ruleset to main: PR required, the "CI gate" check
# required, no force-push and no branch deletion. The GitHub Actions
# integration may bypass it so the coverage-badge commit can still land.
#
# Usage: ./scripts/protect-main.sh

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

RULESET_NAME="main-protection"
REQUIRED_CHECK="CI gate"

if ! command -v gh >/dev/null 2>&1; then
    echo "Error: 'gh' CLI is required. Install GitHub CLI and authenticate." >&2
    exit 1
fi

echo "Enabling repository auto-merge (required by 'dev auto-merge')..."
gh api -X PATCH "repos/{owner}/{repo}" -F allow_auto_merge=true >/dev/null

read -r -d '' PAYLOAD <<JSON || true
{
  "name": "$RULESET_NAME",
  "target": "branch",
  "enforcement": "active",
  "conditions": {
    "ref_name": {
      "include": ["refs/heads/main"],
      "exclude": []
    }
  },
  "bypass_actors": [
    {
      "actor_id": 15368,
      "actor_type": "integration",
      "bypass_mode": "always"
    }
  ],
  "rules": [
    {
      "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 0,
        "dismiss_stale_reviews_on_push": false,
        "require_code_owner_review": false,
        "require_last_push_approval": false,
        "required_review_thread_resolution": false
      }
    },
    {
      "type": "required_status_checks",
      "parameters": {
        "strict_required_status_checks_policy": false,
        "required_status_checks": [
          {
            "context": "$REQUIRED_CHECK"
          }
        ]
      }
    },
    {
      "type": "non_fast_forward"
    },
    {
      "type": "deletion"
    }
  ]
}
JSON

EXISTING_ID="$(gh api "repos/{owner}/{repo}/rulesets" \
    --jq ".[] | select(.name == \"$RULESET_NAME\") | .id" 2>/dev/null || true)"

if [ -n "$EXISTING_ID" ]; then
    echo "Updating ruleset '$RULESET_NAME' (id $EXISTING_ID)..."
    printf '%s' "$PAYLOAD" | gh api -X PUT "repos/{owner}/{repo}/rulesets/$EXISTING_ID" --input - >/dev/null
else
    echo "Creating ruleset '$RULESET_NAME'..."
    printf '%s' "$PAYLOAD" | gh api -X POST "repos/{owner}/{repo}/rulesets" --input - >/dev/null
fi

echo "Done. 'main' now requires a pull request and the '$REQUIRED_CHECK' check."
