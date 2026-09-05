@echo off
setlocal
REM ===== RAG Systems Test Script (Windows) =====
REM Usage: test.bat [options] [module]
REM   test.bat                     - Run all tests (incl. architecture tests)
REM   test.bat --coverage          - Run tests with coverage (JaCoCo)
REM   test.bat rag-basic           - Run only a specific module
REM   test.bat rag-basic --coverage - Combine module + coverage
REM Architecture tests (ArchitectureTest) always run as part of the test suite.

set "ROOT=%~dp0.."
cd /d "%ROOT%"

REM Bootstrap root .env files from scripts\.env*.example (idempotent)
call scripts\bootstrap-env.bat

set "COVERAGE_GOAL=test"
set "MODULE_SPEC="

:parse
if "%~1"=="" goto :done
if "%~1"=="--coverage" (
    set "COVERAGE_GOAL=verify"
) else if "%~1"=="install" (
    call mvnw.cmd install -DskipTests -Djacoco.skip=true >nul 2>&1
) else (
    set "MODULE_SPEC=-pl apps/%~1 -am"
)
shift
goto :parse
:done

if "%MODULE_SPEC%"=="" (
    echo Running tests for all modules ^(goal: %COVERAGE_GOAL%^)...
    call mvnw.cmd %COVERAGE_GOAL%
) else (
    echo Running tests for %MODULE_SPEC% ^(goal: %COVERAGE_GOAL%^)...
    call mvnw.cmd %MODULE_SPEC% %COVERAGE_GOAL%
)

echo.
echo Test complete.
endlocal
