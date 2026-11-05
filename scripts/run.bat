@echo off
setlocal enabledelayedexpansion
REM ===== RAG Systems Run Script (Windows) =====
REM Usage: run.bat [<module>] [--profile <name>] [--args <spring args>] [--force] [--list]
REM   run.bat                          - Run the TUI (default) with local profile
REM   run.bat --profile cloud          - Run the TUI with cloud profile
REM   run.bat rag-basic --profile cloud - Run another module
REM   run.bat --force                  - Force full rebuild before running
REM   run.bat --list                   - List runnable modules and exit
REM <module> accepts a bare name (rag-basic) or a path form (apps\rag-basic).

set "ROOT=%~dp0.."
cd /d "%ROOT%"

set "MODULE="
set "PROFILES=local"
set "EXTRA_ARGS="
set "FORCE="
set "LIST_ONLY="

:parse
if "%~1"=="" goto :parsed
if /I "%~1"=="--profile" (
    if "%~2"=="" (
        echo Missing value for --profile
        endlocal
        exit /b 2
    )
    set "PROFILES=%~2"
    shift
    shift
    goto :parse
)
if /I "%~1"=="--args" (
    if "%~2"=="" (
        echo Missing value for --args
        endlocal
        exit /b 2
    )
    set "EXTRA_ARGS=%~2"
    shift
    shift
    goto :parse
)
if /I "%~1"=="--force" (
    set "FORCE=true"
    shift
    goto :parse
)
if /I "%~1"=="--list" (
    set "LIST_ONLY=true"
    shift
    goto :parse
)
set "ARG=%~1"
if "!ARG:~0,2!"=="--" (
    echo Unknown option: !ARG!
    goto :usage
)
set "MODULE=!ARG!"
shift
goto :parse

:parsed
if defined LIST_ONLY (
    echo Runnable modules ^(Spring Boot apps^):
    call scripts\modules.bat list --runnable
    endlocal
    exit /b 0
)
if "!MODULE!"=="" set "MODULE=rag-tui"
if /I "!MODULE:~0,5!"=="apps\" set "MODULE=!MODULE:~5!"
if "!MODULE:~-1!"=="\" set "MODULE=!MODULE:~0,-1!"
if "!MODULE:~-1!"=="/" set "MODULE=!MODULE:~0,-1!"
call scripts\modules.bat check --runnable "!MODULE!"
if errorlevel 1 (
    echo Unknown runnable module: !MODULE!
    echo Valid runnable modules:
    call scripts\modules.bat list --runnable
    echo Hint: run.bat --list lists them, or omit the module to start rag-tui.
    endlocal
    exit /b 1
)

REM Bootstrap .env.secrets from scripts\.env.secrets.example (idempotent)
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
exit /b %errorlevel%

:usage
echo Usage: run.bat [^<module^>] [--profile ^<name^>] [--args ^<spring args^>] [--force] [--list]
echo   run.bat                  - Run the TUI (default) with local profile
echo   run.bat --profile cloud  - Run the TUI with cloud profile
echo   run.bat rag-basic        - Run another module
echo   run.bat --force          - Force full rebuild before running
echo   run.bat --list           - List runnable modules and exit
echo ^<module^> accepts a bare name ^(rag-basic^) or a path form ^(apps\rag-basic^).
endlocal
exit /b 2
