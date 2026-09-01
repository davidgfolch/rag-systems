@echo off
setlocal
REM ===== RAG Systems Test Script (Windows) =====
REM Usage: test.bat [options] [module]
REM   test.bat                     - Run all tests
REM   test.bat --coverage          - Run tests with coverage (JaCoCo)
REM   test.bat --architecture      - Run only architecture tests
REM   test.bat rag-basic           - Run only a specific module
REM   test.bat rag-basic --coverage - Combine module + coverage

set "ROOT=%~dp0.."
cd /d "%ROOT%"

set "ARCHITECTURE_FLAG="
set "COVERAGE_GOAL=test"
set "MODULE_SPEC="

:parse
if "%~1"=="" goto :done
if "%~1"=="--coverage" (
    set "COVERAGE_GOAL=verify"
) else if "%~1"=="--architecture" (
    set "ARCHITECTURE_FLAG=-Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false"
) else if "%~1"=="install" (
    call mvnw.cmd install -DskipTests >nul 2>&1
) else (
    set "MODULE_SPEC=-pl apps/%~1 -am"
)
shift
goto :parse
:done

if "%MODULE_SPEC%"=="" (
    echo Running tests for all modules ^(goal: %COVERAGE_GOAL%^)...
    call mvnw.cmd %COVERAGE_GOAL% %ARCHITECTURE_FLAG%
) else (
    echo Running tests for %MODULE_SPEC% ^(goal: %COVERAGE_GOAL%^)...
    call mvnw.cmd %MODULE_SPEC% %COVERAGE_GOAL% %ARCHITECTURE_FLAG%
)

echo.
echo Test complete.
endlocal
