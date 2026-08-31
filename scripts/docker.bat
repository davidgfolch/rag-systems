@echo off
setlocal
REM ===== RAG Systems Docker Script (Windows) =====
REM Usage: docker.bat <command> [profile]
REM   docker.bat up              - Start base services (PostgreSQL/pgvector)
REM   docker.bat up-obs          - Start base + observability (Prometheus, Grafana)
REM   docker.bat down            - Stop services
REM   docker.bat logs            - View logs
REM   docker.bat ps              - List running services

set "ROOT=%~dp0.."
cd /d "%ROOT%"

set "CMD=%~1"

if "%CMD%"=="" (
    echo Usage: docker.bat ^<command^> [up|up-obs|down|logs|ps]
    exit /b 1
)

if "%CMD%"=="up" (
    echo Starting base services (PostgreSQL)...
    docker compose -f docker/docker-compose.yml up -d
) else if "%CMD%"=="up-obs" (
    echo Starting base + observability services...
    docker compose -f docker/docker-compose.yml -f docker/docker-compose.observability.yml up -d
) else if "%CMD%"=="down" (
    echo Stopping services...
    docker compose -f docker/docker-compose.yml -f docker/docker-compose.observability.yml down
) else if "%CMD%"=="logs" (
    docker compose -f docker/docker-compose.yml -f docker/docker-compose.observability.yml logs -f
) else if "%CMD%"=="ps" (
    docker compose -f docker/docker-compose.yml -f docker/docker-compose.observability.yml ps
) else (
    echo Unknown command: %CMD%
    echo Usage: docker.bat ^<command^> [up|up-obs|down|logs|ps]
    exit /b 1
)

endlocal
