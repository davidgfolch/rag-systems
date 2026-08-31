@echo off
setlocal
REM ===== RAG Systems Install Script (Windows) =====
REM Usage: install.bat [module]
REM   install.bat          - Install all modules
REM   install.bat rag-basic - Install specific module

set "ROOT=%~dp0.."
cd /d "%ROOT%"

if "%~1"=="" (
    echo Installing all RAG modules...
    call mvnw.cmd clean install -DskipTests
) else (
    echo Installing module: %~1
    call mvnw.cmd clean install -pl "apps/%~1" -am -DskipTests
)

echo.
echo Install complete.
endlocal
