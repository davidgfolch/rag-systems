@echo off
setlocal
REM ===== RAG Systems Docker Script (Windows) =====
REM Usage: docker.bat <command> [profile]
REM   docker.bat up              - Start base services (PostgreSQL/pgvector)
REM   docker.bat up-obs          - Start base + observability (Prometheus, Grafana)
REM   docker.bat up-sonar        - Start base + SonarQube
REM   docker.bat up-all          - Start all services (obs + SonarQube)
REM   docker.bat down            - Stop all services
REM   docker.bat logs            - View logs
REM   docker.bat ps              - List running services

set "ROOT=%~dp0.."
cd /d "%ROOT%"

set "CMD=%~1"

if "%CMD%"=="" (
    echo Usage: docker.bat ^<command^> [up^|up-obs^|up-sonar^|up-all^|down^|logs^|ps]
    exit /b 1
)

set "COMPOSE_BASE=-f docker/docker-compose.yml"
set "COMPOSE_OBS=-f docker/docker-compose.yml -f docker/docker-compose.observability.yml"
set "COMPOSE_SONAR=-f docker/docker-compose.yml -f docker/docker-compose.sonarqube.yml"
set "COMPOSE_ALL=-f docker/docker-compose.yml -f docker/docker-compose.observability.yml -f docker/docker-compose.sonarqube.yml"

if "%CMD%"=="up" (
    echo Starting base services ^(PostgreSQL^)...
    docker compose %COMPOSE_BASE% up -d
) else if "%CMD%"=="up-obs" (
    echo Starting base + observability services...
    docker compose %COMPOSE_OBS% up -d
) else if "%CMD%"=="up-sonar" (
    echo Starting base + SonarQube services...
    docker compose %COMPOSE_SONAR% up -d
) else if "%CMD%"=="up-all" (
    echo Starting all services ^(observability + SonarQube^)...
    docker compose %COMPOSE_ALL% up -d
) else if "%CMD%"=="down" (
    echo Stopping services...
    docker compose %COMPOSE_ALL% down
) else if "%CMD%"=="logs" (
    docker compose %COMPOSE_ALL% logs -f
) else if "%CMD%"=="ps" (
    docker compose %COMPOSE_ALL% ps
) else (
    echo Unknown command: %CMD%
    echo Usage: docker.bat ^<command^> [up^|up-obs^|up-sonar^|up-all^|down^|logs^|ps]
    exit /b 1
)

endlocal
