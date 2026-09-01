param(
    [string]$ProjectKey = "com.rag:rag-systems",
    [string]$HostUrl = "http://localhost:9000",
    [string]$RepoRoot = "."
)

$ErrorActionPreference = "Stop"
$api = "$HostUrl/api"
$mdStart = "<!-- SONARQUBE_RESULTS_START -->"
$mdEnd = "<!-- SONARQUBE_RESULTS_END -->"
$metricKeys = "bugs,vulnerabilities,security_hotspots,code_smells,coverage,duplicated_lines_density"

$tempDir = Join-Path $env:TEMP "rag-sonar"
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null
$probeFile = Join-Path $tempDir "probe.json"

$ready = $false
for ($i = 0; $i -lt 12; $i++) {
    try {
        Invoke-RestMethod -Uri "$api/measures/component?component=$ProjectKey&metricKeys=coverage" -TimeoutSec 10 | Out-Null
        $ready = $true
        break
    } catch {
        Start-Sleep -Seconds 5
    }
}
if (-not $ready) {
    Write-Warning "SonarQube results not available yet; skipping README update."
    exit 0
}

$gate = Invoke-RestMethod -Uri "$api/qualitygates/project_status?projectKey=$ProjectKey" -TimeoutSec 15
$measures = Invoke-RestMethod -Uri "$api/measures/component?component=$ProjectKey&metricKeys=$metricKeys" -TimeoutSec 15

$lookup = @{}
foreach ($m in $measures.component.measures) { $lookup[$m.metric] = $m.value }

function Get-Metric([string]$name) {
    if ($lookup.ContainsKey($name)) { return [string]$lookup[$name] }
    return "n/a"
}
function Format-Pct([string]$name) {
    $v = Get-Metric $name
    if ($v -eq "n/a") { return "n/a" }
    return "$v%"
}

$stamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd HH:mm")
$status = [string]$gate.projectStatus.status
$block = @(
    "| Metric | Value |",
    "|--------|-------|",
    "| Quality Gate | $status |",
    "| Bugs | $(Get-Metric 'bugs') |",
    "| Vulnerabilities | $(Get-Metric 'vulnerabilities') |",
    "| Security Hotspots | $(Get-Metric 'security_hotspots') |",
    "| Code Smells | $(Get-Metric 'code_smells') |",
    "| Coverage | $(Format-Pct 'coverage') |",
    "| Duplication | $(Format-Pct 'duplicated_lines_density') |",
    "",
    "*Last scan: $stamp UTC*"
) -join "`n"

$readme = Join-Path $RepoRoot "README.md"
$content = [System.IO.File]::ReadAllText($readme)
$startIdx = $content.IndexOf($mdStart)
$endIdx = $content.IndexOf($mdEnd)
if ($startIdx -lt 0 -or $endIdx -lt 0) {
    Write-Error "README.md markers ($mdStart / $mdEnd) not found; cannot update results."
}
$newContent = $content.Substring(0, $startIdx + $mdStart.Length) + "`n" + $block + "`n" + $content.Substring($endIdx)
[System.IO.File]::WriteAllText($readme, $newContent, (New-Object System.Text.UTF8Encoding $false))
Write-Host "README.md updated with SonarQube results (gate: $status, coverage: $(Format-Pct 'coverage'))."