@echo off
setlocal
REM ===== RAG Systems Docker Script (Windows) =====
REM Usage: docker.bat <command> [profile]
REM   docker.bat up              - Start base services (PostgreSQL/pgvector)
REM   docker.bat up-ollama       - Start base + Ollama (reuses an external Ollama if one is already running)
REM   docker.bat up-obs          - Start base + observability (Prometheus, Grafana)
REM   docker.bat up-sonar        - Start base + SonarQube
REM   docker.bat up-all          - Start all services (obs + SonarQube + Ollama)
REM   docker.bat down            - Stop all services
REM   docker.bat logs            - View logs
REM   docker.bat ps              - List running services
REM
REM If INFRA_MODE=auto (default) and an external Ollama already responds on
REM OLLAMA_BASE_URL, the rag-ollama container is skipped and the external one is reused.
REM Prometheus/Grafana host ports rotate to the next free port when their defaults
REM (PROMETHEUS_PORT=9090, GRAFANA_PORT=3000) are already taken.

set "ROOT=%~dp0.."
cd /d "%ROOT%"

REM Bootstrap root .env files from scripts\.env*.example (idempotent)
call scripts\bootstrap-env.bat

REM Load .env (config) then .env.secrets (secrets override) so docker-compose can interpolate vars
for %%F in (.env .env.secrets) do (
    if exist "%%F" (
        for /f "delims=" %%L in ('findstr /b /v "#" "%%F" 2^>nul') do (
            for /f "tokens=1,* delims==" %%A in ("%%L") do (
                if not "%%A"=="" if not "%%B"=="" call set "%%A=%%B"
            )
        )
    )
)

set "CMD=%~1"

if "%CMD%"=="" (
    echo Usage: docker.bat ^<command^> [up^|up-ollama^|up-obs^|up-sonar^|up-all^|down^|logs^|ps]
    exit /b 1
)

if not defined INFRA_MODE set "INFRA_MODE=auto"
if not defined OLLAMA_BASE_URL set "OLLAMA_BASE_URL=http://localhost:11434"
if not defined PROMETHEUS_PORT set "PROMETHEUS_PORT=9090"
if not defined GRAFANA_PORT set "GRAFANA_PORT=3000"

set "COMPOSE_BASE=-f docker/docker-compose.yml"
set "COMPOSE_SONAR=-f docker/docker-compose.yml -f docker/docker-compose.sonarqube.yml"
set "COMPOSE_ALL=-f docker/docker-compose.yml -f docker/docker-compose.observability.yml -f docker/docker-compose.sonarqube.yml -f docker/docker-compose.ollama.yml"

set "COMPOSE_SET=%COMPOSE_BASE%"
set "OP=up -d"
set "SELECT_OLLAMA=0"
set "SELECT_OBS=0"

if "%CMD%"=="up" (
    goto :run
) else if "%CMD%"=="up-ollama" (
    if /i "%INFRA_MODE%"=="local" (
        set "SELECT_OLLAMA=1"
    ) else (
        curl -sf --max-time 3 "%OLLAMA_BASE_URL%/api/tags" >nul 2>&1
        if errorlevel 1 (
            set "SELECT_OLLAMA=1"
        ) else (
            echo External Ollama detected at %OLLAMA_BASE_URL% - reusing it, skipping rag-ollama
        )
    )
    goto :run
) else if "%CMD%"=="up-obs" (
    set "SELECT_OBS=1"
    goto :run
) else if "%CMD%"=="up-sonar" (
    set "COMPOSE_SET=%COMPOSE_SONAR%"
    goto :run
) else if "%CMD%"=="up-all" (
    set "SELECT_OBS=1"
    if /i "%INFRA_MODE%"=="local" (
        set "SELECT_OLLAMA=1"
    ) else (
        curl -sf --max-time 3 "%OLLAMA_BASE_URL%/api/tags" >nul 2>&1
        if errorlevel 1 (
            set "SELECT_OLLAMA=1"
        ) else (
            echo External Ollama detected at %OLLAMA_BASE_URL% - reusing it, skipping rag-ollama
        )
    )
    goto :run
) else if "%CMD%"=="down" (
    set "COMPOSE_SET=%COMPOSE_ALL%"
    set "OP=down"
    goto :run
) else if "%CMD%"=="logs" (
    set "COMPOSE_SET=%COMPOSE_ALL%"
    set "OP=logs -f"
    goto :run
) else if "%CMD%"=="ps" (
    set "COMPOSE_SET=%COMPOSE_ALL%"
    set "OP=ps"
    goto :run
) else (
    echo Unknown command: %CMD%
    echo Usage: docker.bat ^<command^> [up^|up-ollama^|up-obs^|up-sonar^|up-all^|down^|logs^|ps]
    exit /b 1
)

:run
if "%SELECT_OLLAMA%"=="1" set "COMPOSE_SET=%COMPOSE_SET% -f docker/docker-compose.ollama.yml"
if "%SELECT_OBS%"=="1" (
    call :rotate_obs_ports
    if errorlevel 1 exit /b 1
    set "COMPOSE_SET=%COMPOSE_SET% -f docker/docker-compose.observability.yml"
)

echo Running: docker compose %COMPOSE_SET% %OP%
docker compose %COMPOSE_SET% %OP%
set "DOCKER_EXIT=%ERRORLEVEL%"

REM Sync postgres password to the generated PGVECTOR_PASSWORD secret after any up
if "%CMD:~0,2%"=="up" call scripts\pg-pw.bat

exit /b %DOCKER_EXIT%

:rotate_obs_ports
call :find_free_port %PROMETHEUS_PORT%
if errorlevel 1 (
    echo ERROR: no free port found for Prometheus starting at %PROMETHEUS_PORT% >&2
    exit /b 1
)
set "PROMETHEUS_PORT=%FREE_PORT%"
call :find_free_port %GRAFANA_PORT%
if errorlevel 1 (
    echo ERROR: no free port found for Grafana starting at %GRAFANA_PORT% >&2
    exit /b 1
)
set "GRAFANA_PORT=%FREE_PORT%"
echo Host ports -^> Prometheus: %PROMETHEUS_PORT%, Grafana: %GRAFANA_PORT% ^(rotated to free ports if needed^)
exit /b 0

:find_free_port
set "FREE_PORT="
set /a "PORT_START=%~1"
set /a "PORT_END=%PORT_START%+10"
for /L %%p in (%PORT_START%,1,%PORT_END%) do (
    call :is_port_in_use %%p
    if errorlevel 1 (
        set "FREE_PORT=%%p"
        exit /b 0
    )
)
exit /b 1

:is_port_in_use
REM Returns errorlevel 0 if the port accepts a TCP connection (in use), 1 if free.
powershell -NoProfile -Command "try { $c = New-Object System.Net.Sockets.TcpClient; $c.Connect('127.0.0.1',%1); $c.Close(); exit 0 } catch { exit 1 }" >nul 2>&1
exit /b %ERRORLEVEL%
