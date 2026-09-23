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
        for /f %%P in ('powershell -NoProfile -Command "$s='abcdef0123456789'; -join (1..32 | ForEach-Object { $s[(Get-Random -Max 16)] })"') do set "PASSWORD=%%P"
        powershell -NoProfile -Command "(Get-Content '.env.secrets') -replace '^PGVECTOR_PASSWORD=$','PGVECTOR_PASSWORD=!PASSWORD!' | Set-Content '.env.secrets'"
        echo Generated PGVECTOR_PASSWORD in .env.secrets
    )
)

REM --- Fill blank SONAR_ADMIN_PASSWORD in .env.secrets ---
if exist ".env.secrets" (
    set "SAPW_LINE="
    for /f "delims=" %%L in ('findstr /b /c:"SONAR_ADMIN_PASSWORD=" ".env.secrets"') do set "SAPW_LINE=%%L"
    if "!SAPW_LINE!"=="SONAR_ADMIN_PASSWORD=" (
        for /f %%P in ('powershell -NoProfile -Command "$u='ABCDEFGHIJKLMNOPQRSTUVWXYZ'; $l='abcdefghijklmnopqrstuvwxyz'; $d='0123456789'; $all=$u+$l+$d; $pw=($u[(Get-Random -Max $u.Length)],$l[(Get-Random -Max $l.Length)],$d[(Get-Random -Max $d.Length)] + -join (1..29 | ForEach-Object { $all[(Get-Random -Max $all.Length)] }) -join ''; $pw"') do set "PASSWORD=%%P"
        powershell -NoProfile -Command "(Get-Content '.env.secrets') -replace '^SONAR_ADMIN_PASSWORD=$','SONAR_ADMIN_PASSWORD=!PASSWORD!' | Set-Content '.env.secrets'"
        echo Generated SONAR_ADMIN_PASSWORD in .env.secrets
    )
)

endlocal
exit /b 0
