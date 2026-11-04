@echo off
setlocal enabledelayedexpansion
REM ===== Bootstrap env: copy scripts\.env.secrets.example -> root .env.secrets + generate passwords (Windows) =====
REM Idempotent: never overwrites an existing root .env.secrets file; only fills blank passwords.
REM Called automatically by docker.bat / run.bat / sonar.bat / install.bat / build.bat / test.bat.

set "ROOT=%~dp0.."
cd /d "%ROOT%"

REM --- Copy scripts\.env.secrets.example to root .env.secrets if it does not exist ---
if exist "scripts\.env.secrets.example" if not exist ".env.secrets" (
    copy "scripts\.env.secrets.example" ".env.secrets" >nul
    echo Created .env.secrets from scripts\.env.secrets.example
)

REM --- Fill blank PGVECTOR_PASSWORD in .env.secrets ---
if exist ".env.secrets" (
    set "PW_LINE="
    for /f "delims=" %%L in ('findstr /b /c:"PGVECTOR_PASSWORD=" ".env.secrets"') do set "PW_LINE=%%L"
    if "!PW_LINE!"=="PGVECTOR_PASSWORD=" (
        powershell -NoProfile -Command "$s='abcdef0123456789';$p=-join (1..32 | ForEach-Object { $s[(Get-Random -Max 16)] });(Get-Content '.env.secrets') | ForEach-Object { if ($_ -match '^PGVECTOR_PASSWORD=$') { 'PGVECTOR_PASSWORD=' + $p } else { $_ } } | Set-Content '.env.secrets'"
        echo Generated PGVECTOR_PASSWORD in .env.secrets
    )
)

REM --- Fill blank SONAR_ADMIN_PASSWORD in .env.secrets ---
if exist ".env.secrets" (
    set "SAPW_LINE="
    for /f "delims=" %%L in ('findstr /b /c:"SONAR_ADMIN_PASSWORD=" ".env.secrets"') do set "SAPW_LINE=%%L"
    if "!SAPW_LINE!"=="SONAR_ADMIN_PASSWORD=" (
        REM SonarQube 26.8+ requires 12+ chars with upper, lower, digit, and a special char.
        powershell -NoProfile -Command "$u='ABCDEFGHIJKLMNOPQRSTUVWXYZ';$l='abcdefghijklmnopqrstuvwxyz';$d='0123456789';$x='-_.@';$a=$u+$l+$d+$x;$p=-join @($u[(Get-Random -Max 26)],$l[(Get-Random -Max 26)],$d[(Get-Random -Max 10)],$x[(Get-Random -Max 4)]);$i=0;while($i -lt 28){$p+=$a[(Get-Random -Max $a.Length)];$i++};(Get-Content '.env.secrets') | ForEach-Object { if ($_ -match '^SONAR_ADMIN_PASSWORD=$') { 'SONAR_ADMIN_PASSWORD=' + $p } else { $_ } } | Set-Content '.env.secrets'"
        echo Generated SONAR_ADMIN_PASSWORD in .env.secrets
    )
)

endlocal
exit /b 0
