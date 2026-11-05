@echo off
setlocal enabledelayedexpansion
REM ===== RAG Systems Build Script (Windows) =====
REM Usage: build.bat [module] [--list]
REM   build.bat           - Build all modules
REM   build.bat rag-basic - Build a specific module
REM   build.bat --list    - List valid modules and exit
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
        echo Hint: build.bat --list lists them.
        endlocal
        exit /b 1
    )
)

call scripts\bootstrap-env.bat

if "!MODULE!"=="" (
    echo Building all RAG modules...
    call mvnw.cmd clean package
) else (
    echo Building module: !MODULE!
    call mvnw.cmd clean package -pl "apps/!MODULE!" -am
)

echo.
echo Build complete.
endlocal
exit /b %errorlevel%
