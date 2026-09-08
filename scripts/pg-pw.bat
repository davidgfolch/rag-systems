@echo off
setlocal
REM ===== Sync rag-postgres password to the PGVECTOR_PASSWORD secret (Windows) =====
REM Idempotent: runs only when the rag-postgres container is running; skipped otherwise.
REM Called by install.bat and docker.bat so a checkout's postgres matches .env.secrets.

set "ROOT=%~dp0.."
cd /d "%ROOT%"

set "PGVECTOR_PASSWORD="
if exist ".env.secrets" (
    for /f "usebackq tokens=1,* delims==" %%A in (".env.secrets") do (
        if "%%A"=="PGVECTOR_PASSWORD" if not "%%B"=="" set "PGVECTOR_PASSWORD=%%B"
    )
)

if "%PGVECTOR_PASSWORD%"=="" (
    echo pg-pw: PGVECTOR_PASSWORD is empty; run scripts\bootstrap-env first.
    exit /b 0
)

where docker >nul 2>nul
if errorlevel 1 (
    echo pg-pw: docker not installed; postgres password sync skipped.
    exit /b 0
)

set "RUNNING="
for /f "delims=" %%R in ('docker ps --filter "name=rag-postgres" --filter "status=running" --format "{{.Names}}" 2^>nul') do set "RUNNING=%%R"
if not defined RUNNING (
    echo pg-pw: rag-postgres not running; sync skipped ^(applied on first docker up^).
    exit /b 0
)

docker exec rag-postgres psql -U rag -d rag -qc "ALTER USER rag WITH PASSWORD '%PGVECTOR_PASSWORD%';" >nul 2>&1
if errorlevel 1 (
    echo pg-pw: WARNING could not update rag-postgres password ^(container may be unhealthy^).
    exit /b 0
)
echo pg-pw: rag-postgres password synced to PGVECTOR_PASSWORD in .env.secrets.
endlocal
exit /b 0