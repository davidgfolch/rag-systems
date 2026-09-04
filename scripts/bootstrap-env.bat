@echo off
setlocal enabledelayedexpansion
REM ===== Bootstrap env: copy scripts\.env.*.example -> root + generate passwords (Windows) =====
REM Idempotent: never overwrites an existing root .env file; only fills blank passwords.
REM Called automatically by docker.bat / run.bat / sonar.bat / install.bat / build.bat / test.bat.

set "ROOT=%~dp0.."
cd /d "%ROOT%"

REM --- Copy each scripts\.env*.example to the matching root file if it does not exist ---
for %%E in (scripts\.env*.example) do (
    if exist "%%E" (
        set "BASE=%%~nxE"
        set "NAME=!BASE:.example=!"
        if not exist "!NAME!" (
            copy "scripts\!BASE!" "!NAME!" >nul
            echo Created !NAME! from scripts\!BASE!
        )
    )
)

REM --- Fill blank PGVECTOR_PASSWORD in .env ---
if exist ".env" (
    set "PW_LINE="
    for /f "delims=" %%L in ('findstr /b /c:"PGVECTOR_PASSWORD=" ".env"') do set "PW_LINE=%%L"
    if "!PW_LINE!"=="PGVECTOR_PASSWORD=" (
        for /f %%P in ('powershell -NoProfile -Command "$s='abcdef0123456789'; -join (1..32 | ForEach-Object { $s[(Get-Random -Max 16)] })"') do set "PASSWORD=%%P"
        powershell -NoProfile -Command "(Get-Content '.env') -replace '^PGVECTOR_PASSWORD=$','PGVECTOR_PASSWORD=!PASSWORD!' | Set-Content '.env'"
        echo Generated PGVECTOR_PASSWORD in .env
    )
)

REM --- Fill blank SONAR_ADMIN_PASSWORD in .env.secrets ---
if exist ".env.secrets" (
    set "SAPW_LINE="
    for /f "delims=" %%L in ('findstr /b /c:"SONAR_ADMIN_PASSWORD=" ".env.secrets"') do set "SAPW_LINE=%%L"
    if "!SAPW_LINE!"=="SONAR_ADMIN_PASSWORD=" (
        for /f %%P in ('powershell -NoProfile -Command "$s='abcdef0123456789'; -join (1..32 | ForEach-Object { $s[(Get-Random -Max 16)] })"') do set "PASSWORD=%%P"
        powershell -NoProfile -Command "(Get-Content '.env.secrets') -replace '^SONAR_ADMIN_PASSWORD=$','SONAR_ADMIN_PASSWORD=!PASSWORD!' | Set-Content '.env.secrets'"
        echo Generated SONAR_ADMIN_PASSWORD in .env.secrets
    )
)

endlocal
exit /b 0
