@echo off
setlocal
REM ===== RAG Systems SonarQube Analysis Script (Windows) =====
REM Usage: sonar.bat <command> [token]
REM   sonar.bat up             - Start SonarQube via Docker
REM   sonar.bat scan [token]   - Run tests + JaCoCo + SonarQube analysis
REM   sonar.bat up-scan [token]- Start SonarQube, wait for ready, then scan
REM   sonar.bat down           - Stop SonarQube
REM Token: passed as 2nd arg or set SONAR_TOKEN env var.

set "ROOT=%~dp0.."
cd /d "%ROOT%"

set "CMD=%~1"
set "TOKEN=%~2"
if "%TOKEN%"=="" set "TOKEN=%SONAR_TOKEN%"

if "%CMD%"=="" (
    echo Usage: sonar.bat ^<command^> [up^|scan^|up-scan^|down] [token]
    exit /b 1
)

set "COMPOSE_BASE=-f docker/docker-compose.yml"
set "COMPOSE_SONAR=-f docker/docker-compose.yml -f docker/docker-compose.sonarqube.yml"
set "SCAN_ARGS=-Dsonar.host.url=http://localhost:9000 -Dsonar.scanner.skipJreProvisioning=true -DskipTests=false"

if "%CMD%"=="up" (
    echo Starting SonarQube...
    docker compose %COMPOSE_SONAR% up -d
    echo.
    echo SonarQube starting at http://localhost:9000
    echo Default credentials: admin / admin
    echo Wait ~30 seconds for the server to be ready before running scan.
) else if "%CMD%"=="scan" (
    if "%TOKEN%"=="" (
        echo Error: no token. Pass it as an argument or set SONAR_TOKEN.
        exit /b 1
    )
    echo Running tests with coverage + SonarQube analysis...
    call mvnw.cmd verify sonar:sonar -Dsonar.token=%TOKEN% %SCAN_ARGS%
    if not errorlevel 1 call :export_results
) else if "%CMD%"=="up-scan" (
    echo Starting SonarQube...
    docker compose %COMPOSE_SONAR% up -d
    echo Waiting for SonarQube to be ready...
    :wait_loop
    timeout /t 5 /nobreak >nul 2>&1
    curl -sf http://localhost:9000/api/system/status >nul 2>&1
    if errorlevel 1 goto :wait_loop
    echo SonarQube is ready.
    if "%TOKEN%"=="" (
        echo Error: no token. Pass it as an argument or set SONAR_TOKEN.
        exit /b 1
    )
    echo Running tests with coverage + SonarQube analysis...
    call mvnw.cmd verify sonar:sonar -Dsonar.token=%TOKEN% %SCAN_ARGS%
    if not errorlevel 1 call :export_results
) else if "%CMD%"=="down" (
    echo Stopping SonarQube...
    docker compose %COMPOSE_SONAR% down
) else (
    echo Unknown command: %CMD%
    echo Usage: sonar.bat ^<command^> [up^|scan^|up-scan^|down] [token]
    exit /b 1
)

echo.
echo SonarQube operation complete.
endlocal
exit /b 0

:export_results
echo Exporting SonarQube results to README.md...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "scripts\sonar-export.ps1" -RepoRoot "%ROOT%"
goto :eof
