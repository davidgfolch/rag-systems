param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
$readme = Join-Path $Root 'README.md'
$startMarker = '<!-- COVERAGE_BADGES_START -->'
$endMarker = '<!-- COVERAGE_BADGES_END -->'
$moduleList = @('rag-common','rag-basic','rag-memory','rag-webcrawler','rag-advanced','rag-agentic','rag-evaluation','rag-observability','rag-cli','rag-tui')

Write-Host "Generating per-module coverage badges..."

$badges = @()
foreach ($mod in $moduleList) {
    $csv = Join-Path $Root "apps\$mod\target\site\jacoco\jacoco.csv"
    if (-not (Test-Path $csv)) {
        Write-Host "  Skipping ${mod}: no JaCoCo CSV report"
        continue
    }
    $rows = Get-Content $csv | Select-Object -Skip 1
    if ($rows.Count -eq 0) {
        Write-Host "  Skipping ${mod}: empty CSV"
        continue
    }
    $missed = 0; $covered = 0
    foreach ($r in $rows) {
        $cols = $r -split ','
        $missed += [int]$cols[3]
        $covered += [int]$cols[4]
    }
    if ($missed -eq 0 -and $covered -eq 0) {
        Write-Host "  Skipping ${mod}: zero instruction data"
        continue
    }
    $pct = if ($missed -eq 0) { 100 } else { [math]::Round(($covered / ($covered + $missed)) * 100) }
    $color = if ($pct -ge 85) { 'brightgreen' } elseif ($pct -ge 70) { 'yellowgreen' } elseif ($pct -ge 50) { 'yellow' } else { 'red' }
    Write-Host "  ${mod}: ${pct}% coverage (color: $color)"
    $encoded = $mod -replace '-', '--'
    $url = "https://img.shields.io/badge/${encoded}-${pct}%25-${color}"
    $badges += "![${mod}](${url})"
}

if ($badges.Count -eq 0) {
    Write-Host 'No modules with coverage data found; skipping README update.'
    exit 0
}

$badgeRow = $badges -join '  '
$content = Get-Content $readme
$startIdx = [Array]::IndexOf($content, $startMarker)
$endIdx = [Array]::IndexOf($content, $endMarker)

if ($startIdx -lt 0 -or $endIdx -lt 0) {
    Write-Error 'Markers not found in README.md'
    exit 1
}

$newContent = $content[0..$startIdx] + $badgeRow + $content[$endIdx..($content.Count-1)]
$newContent | Set-Content -Encoding UTF8 $readme
Write-Host 'Updated coverage badges in README.md'
Write-Host "Done. Coverage badges generated for $($badges.Count) module(s)."