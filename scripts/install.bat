@echo off
setlocal enabledelayedexpansion
REM ===== RAG Systems Install Script (Windows) =====
REM Usage: install.bat [module] [--list]
REM   install.bat          - Install all modules
REM   install.bat rag-basic - Install specific module
REM   install.bat --list   - List valid modules and exit
REM <module> accepts a bare name (rag-basic) or a path form (apps\rag-basic).

set "ROOT=%~dp0.."
cd /d "%ROOT%"

set "MODULE=%~1"
if /I "%MODULE%"=="--list" (
    echo Valid modules:
    call scripts\modules.bat list
    endlocal
    exit /b 0
)
if defined MODULE (
    if "!MODULE:~0,1!"=="-" (
        echo Unknown option: !MODULE!
        endlocal
        exit /b 2
    )
    if /I "!MODULE:~0,5!"=="apps\" set "MODULE=!MODULE:~5!"
    if "!MODULE:~-1!"=="\" set "MODULE=!MODULE:~0,-1!"
    if "!MODULE:~-1!"=="/" set "MODULE=!MODULE:~0,-1!"
    call scripts\modules.bat check "!MODULE!"
    if errorlevel 1 (
        echo Unknown module: !MODULE!
        echo Valid modules:
        call scripts\modules.bat list
        echo Hint: install.bat --list lists them.
        endlocal
        exit /b 1
    )
)

REM Bootstrap .env.secrets from scripts\.env.secrets.example (idempotent)
call scripts\bootstrap-env.bat

REM Sync docker postgres password to the generated PGVECTOR_PASSWORD secret (idempotent)
call scripts\pg-pw.bat

if "!MODULE!"=="" (
    echo Installing all RAG modules...
    call mvnw.cmd clean install -DskipTests -Djacoco.skip=true
) else (
    echo Installing module: !MODULE!
    call mvnw.cmd clean install -pl "apps/!MODULE!" -am -DskipTests -Djacoco.skip=true
)
if not errorlevel 1 call :codegraph

echo.
echo Install complete.
endlocal
exit /b 0

:codegraph
where codegraph >nul 2>nul
if not errorlevel 1 goto :codegraph_configured
echo.
echo ===== CodeGraph auto-configuration =====
echo Installing CodeGraph CLI...
REM Clear prior install so the installer extracts a pristine bundle (it is not safe to re-install over stale/corrupted state).
if exist "%LOCALAPPDATA%\codegraph\current" rmdir /s /q "%LOCALAPPDATA%\codegraph\current"
powershell -NoProfile -ExecutionPolicy Bypass -Command "irm https://raw.githubusercontent.com/colbymchenry/codegraph/main/install.ps1 | iex"
if errorlevel 1 (
    echo Warning: CodeGraph CLI install failed. Skipping agent wiring.
    goto :eof
)
REM Locate the freshly installed launcher and put its bin dir on this session's PATH.
set "CG_ROOT=%LOCALAPPDATA%\codegraph"
set "CODEGRAPH_BIN="
for /f "delims=" %%F in ('dir /b /s "%CG_ROOT%\codegraph.cmd" 2^>nul') do set "CODEGRAPH_BIN=%%~dpF"
if defined CODEGRAPH_BIN set "PATH=%CODEGRAPH_BIN%;%PATH%"
where codegraph >nul 2>nul
if not errorlevel 1 goto :codegraph_configured

REM Fallback: reload the freshly updated user PATH from the registry into this session.
for /f "usebackq tokens=2,* delims=	 " %%A in (`reg query "HKCU\Environment" /v Path 2^>nul`) do if not "%%B"=="" set "PATH=%%B;%PATH%"
where codegraph >nul 2>nul
if errorlevel 1 (
    echo Warning: CodeGraph CLI not found on PATH after install. Skipping agent wiring.
    goto :eof
)
:codegraph_configured
echo Wiring CodeGraph into configured agents...
codegraph install || echo Warning: CodeGraph agent wiring failed.
echo Building CodeGraph index for this project...
codegraph init || echo Warning: CodeGraph init failed.
goto :eof
