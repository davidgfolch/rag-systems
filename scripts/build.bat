@echo off
setlocal
REM ===== RAG Systems Build Script (Windows) =====
REM Usage: build.bat [module]
REM   build.bat           - Build all modules
REM   build.bat rag-basic - Build a specific module

set "ROOT=%~dp0.."
cd /d "%ROOT%"

REM Bootstrap root .env files from scripts\.env*.example (idempotent)
call scripts\bootstrap-env.bat

if "%~1"=="" (
    echo Building all RAG modules...
    call mvnw.cmd clean package
) else (
    echo Building module: %~1
    call mvnw.cmd clean package -pl "apps/%~1" -am
)

echo.
echo Build complete.
endlocal
