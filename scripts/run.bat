@echo off
setlocal enabledelayedexpansion
REM ===== RAG Systems Run Script (Windows) =====
REM Usage: run.bat [<module>] [--profile <name>] [--args <spring args>] [--force]
REM   run.bat                          - Run the TUI (default) with local profile
REM   run.bat --profile cloud          - Run the TUI with cloud profile
REM   run.bat rag-basic --profile cloud - Run another module
REM   run.bat --force                  - Force full rebuild before running

set "ROOT=%~dp0.."
cd /d "%ROOT%"

REM Bootstrap root .env files from scripts\.env*.example (idempotent)
call scripts\bootstrap-env.bat

REM Load .env (config) then .env.secrets (secrets override) into the environment
for %%F in (.env .env.secrets) do (
    if exist "%%F" (
        for /f "delims=" %%L in ('findstr /b /v "#" "%%F" 2^>nul') do (
            for /f "tokens=1,* delims==" %%A in ("%%L") do (
                if not "%%A"=="" if not "%%B"=="" call set "%%A=%%B"
            )
        )
    )
)

set "PROFILES=local"
set "EXTRA_ARGS="
set "FORCE="

set "FIRST=%~1"
if not defined FIRST goto :nofirst
set "FIRST_IS_FLAG="
if "%FIRST:~0,2%"=="--" set "FIRST_IS_FLAG=1"
if defined FIRST_IS_FLAG (
    set "MODULE="
) else (
    set "MODULE=%FIRST%"
    shift
)
:nofirst

:parse
if "%~1"=="" goto :done
if "%~1"=="--profile" (
    set "PROFILES=%~2"
    shift
    shift
) else if "%~1"=="--args" (
    set "EXTRA_ARGS=%~2"
    shift
    shift
) else if "%~1"=="--force" (
    set "FORCE=true"
    shift
) else (
    shift
)
goto :parse
:done

if "%MODULE%"=="" set "MODULE=rag-tui"

set "NEED_BUILD=true"
if not defined FORCE (
    for /f %%T in ('git log -1 --format^=%%ct 2^>nul') do set "COMMIT_TIME=%%T"
    if defined COMMIT_TIME (
        if exist "apps\.build-marker" (
            for /f %%M in ('type "apps\.build-marker"') do set "MARKER_TIME=%%M"
            if defined MARKER_TIME if "!MARKER_TIME!"=="!COMMIT_TIME!" set "NEED_BUILD=false"
        )
    )
)

if "!NEED_BUILD!"=="true" (
    echo Building reactor (skip tests^)...
    call mvnw.cmd install -DskipTests -Djacoco.skip=true
    for /f %%T in ('git log -1 --format^=%%ct 2^>nul') do echo %%T>apps\.build-marker
) else (
    echo No source changes detected, skipping build...
)
echo Running %MODULE% with profile: %PROFILES%
call mvnw.cmd spring-boot:run -pl "apps/%MODULE%" -am -Dspring-boot.run.profiles=%PROFILES% -Dspring-boot.run.arguments=%EXTRA_ARGS%
endlocal
