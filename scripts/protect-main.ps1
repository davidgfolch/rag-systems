param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

# ===== RAG Systems main-branch protection (Windows) =====
# Applies a repository ruleset to main: PR required, the "CI gate" check
# required, no force-push and no branch deletion. The GitHub Actions
# integration may bypass it so the coverage-badge commit can still land.
#
# Usage: .\scripts\protect-main.ps1

$ErrorActionPreference = 'Stop'
Set-Location $Root

$RulesetName = 'main-protection'
$RequiredCheck = 'CI gate'

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Error "Error: 'gh' CLI is required. Install GitHub CLI and authenticate."
}

Write-Host "Enabling repository auto-merge (required by 'dev auto-merge')..."
gh api -X PATCH 'repos/{owner}/{repo}' -F allow_auto_merge=true | Out-Null

$payload = @{
    name        = $RulesetName
    target      = 'branch'
    enforcement = 'active'
    conditions  = @{
        ref_name = @{
            include = @('refs/heads/main')
            exclude = @()
        }
    }
    bypass_actors = @(
        @{
            actor_id    = 15368
            actor_type  = 'integration'
            bypass_mode = 'always'
        }
    )
    rules = @(
        @{
            type       = 'pull_request'
            parameters = @{
                required_approving_review_count   = 0
                dismiss_stale_reviews_on_push     = $false
                require_code_owner_review         = $false
                require_last_push_approval        = $false
                required_review_thread_resolution = $false
            }
        },
        @{
            type       = 'required_status_checks'
            parameters = @{
                strict_required_status_checks_policy = $false
                required_status_checks               = @(
                    @{ context = $RequiredCheck }
                )
            }
        },
        @{ type = 'non_fast_forward' },
        @{ type = 'deletion' }
    )
} | ConvertTo-Json -Depth 10

$rulesets = gh api 'repos/{owner}/{repo}/rulesets' | ConvertFrom-Json
$existing = $rulesets | Where-Object { $_.name -eq $RulesetName } | Select-Object -First 1

$payloadFile = Join-Path ([System.IO.Path]::GetTempPath()) "rag-systems-ruleset.json"
Set-Content -Path $payloadFile -Value $payload -Encoding UTF8

try {
    if ($existing) {
        Write-Host "Updating ruleset '$RulesetName' (id $($existing.id))..."
        gh api -X PUT "repos/{owner}/{repo}/rulesets/$($existing.id)" --input $payloadFile | Out-Null
    } else {
        Write-Host "Creating ruleset '$RulesetName'..."
        gh api -X POST 'repos/{owner}/{repo}/rulesets' --input $payloadFile | Out-Null
    }
} finally {
    Remove-Item -Path $payloadFile -ErrorAction SilentlyContinue
}

Write-Host "Done. 'main' now requires a pull request and the '$RequiredCheck' check."
