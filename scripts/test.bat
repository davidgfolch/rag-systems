@echo off
setlocal enabledelayedexpansion
REM ===== RAG Systems Test Script (Windows) =====
REM Usage: test.bat [options] [module]
REM   test.bat                     - Run all tests with coverage (verify; JaCoCo 90% check enforced)
REM   test.bat --coverage          - Alias of the default (verify + JaCoCo check)
REM   test.bat rag-basic           - Run only a specific module (verify)
REM   test.bat rag-basic --coverage - Combine module + verify
REM   test.bat --list              - List valid modules and exit
REM <module> accepts a bare name (rag-basic) or a path form (apps\rag-basic).
REM Architecture tests (ArchitectureTest) always run as part of the test suite.

set "ROOT=%~dp0.."
cd /d "%ROOT%"

set "COVERAGE_GOAL=verify"
set "MODULE_SPEC="
set "LIST_ONLY="
set "DO_INSTALL="

:parse
if "%~1"=="" goto :parsed
if /I "%~1"=="--coverage" (
    set "COVERAGE_GOAL=verify"
    shift
    goto :parse
)
if /I "%~1"=="--list" (
    set "LIST_ONLY=true"
    shift
    goto :parse
)
if /I "%~1"=="install" (
    set "DO_INSTALL=true"
    shift
    goto :parse
)
set "ARG=%~1"
if "!ARG:~0,1!"=="-" (
    echo Unknown option: !ARG!
    endlocal
    exit /b 2
)
set "MODNAME=!ARG!"
if /I "!MODNAME:~0,5!"=="apps\" set "MODNAME=!MODNAME:~5!"
if "!MODNAME:~-1!"=="\" set "MODNAME=!MODNAME:~0,-1!"
if "!MODNAME:~-1!"=="/" set "MODNAME=!MODNAME:~0,-1!"
call scripts\modules.bat check "!MODNAME!"
if errorlevel 1 (
    echo Unknown module: !MODNAME!
    echo Valid modules:
    call scripts\modules.bat list
    echo Hint: test.bat --list lists them.
    endlocal
    exit /b 1
)
set "MODULE_SPEC=-pl apps/!MODNAME! -am"
shift
goto :parse

:parsed
if defined LIST_ONLY (
    echo Valid modules:
    call scripts\modules.bat list
    endlocal
    exit /b 0
)

REM Bootstrap .env.secrets from scripts\.env.secrets.example (idempotent)
call scripts\bootstrap-env.bat

if defined DO_INSTALL call mvnw.cmd install -DskipTests -Djacoco.skip=true >nul 2>&1

if "%MODULE_SPEC%"=="" (
    echo Running tests for all modules ^(goal: %COVERAGE_GOAL%^)...
    call mvnw.cmd %COVERAGE_GOAL%
) else (
    echo Running tests for %MODULE_SPEC% ^(goal: %COVERAGE_GOAL%^)...
    call mvnw.cmd %MODULE_SPEC% %COVERAGE_GOAL%
)

REM If coverage was run, regenerate badges
if "%COVERAGE_GOAL%"=="verify" (
    if exist "scripts\coverage-badges.bat" (
        call scripts\coverage-badges.bat
    )
)

echo.
echo Test complete.
endlocal
exit /b %errorlevel%

:usage
echo Usage: test.bat [options] [module]
echo   test.bat                    - Run all tests with coverage
echo   test.bat --coverage         - Alias of the default
echo   test.bat rag-basic          - Run only a specific module
echo   test.bat --list             - List valid modules and exit
echo ^<module^> accepts a bare name ^(rag-basic^) or a path form ^(apps\rag-basic^).
endlocal
exit /b 2
