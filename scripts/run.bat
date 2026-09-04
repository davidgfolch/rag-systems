@echo off
setlocal
REM ===== RAG Systems Run Script (Windows) =====
REM Usage: run.bat [<module>] [--profile <name>] [--args <spring args>]
REM   run.bat                          - Run the TUI (default) with local profile
REM   run.bat --profile cloud          - Run the TUI with cloud profile
REM   run.bat rag-basic --profile cloud - Run another module

set "ROOT=%~dp0.."
cd /d "%ROOT%"

REM Bootstrap root .env files from scripts\.env*.example (idempotent)
call scripts\bootstrap-env.bat

set "PROFILES=local"
set "EXTRA_ARGS="

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
) else if "%~1"=="--args" (
    set "EXTRA_ARGS=%~2"
    shift
)
shift
goto :parse
:done

if "%MODULE%"=="" set "MODULE=rag-tui"

echo Running %MODULE% with profile: %PROFILES%
call mvnw.cmd spring-boot:run -pl "apps/%MODULE%" -am -Dspring-boot.run.profiles=%PROFILES% -Dspring-boot.run.arguments=%EXTRA_ARGS%
endlocal
