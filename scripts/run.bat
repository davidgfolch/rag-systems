@echo off
setlocal
REM ===== RAG Systems Run Script (Windows) =====
REM Usage: run.bat <module> [--profile <name>] [--args <spring args>]
REM   run.bat rag-basic                 - Run with default (local) profile
REM   run.bat rag-basic --profile cloud - Run with cloud profile
REM   run.bat rag-basic --profile local,observability

set "ROOT=%~dp0.."
cd /d "%ROOT%"

set "MODULE=%~1"
set "PROFILES=local"
set "EXTRA_ARGS="

shift
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

if "%MODULE%"=="" (
    echo Error: module argument required.
    echo Usage: run.bat ^<module^> [--profile ^<name^>]
    exit /b 1
)

echo Running %MODULE% with profile: %PROFILES%
call mvnw.cmd spring-boot:run -pl "apps/%MODULE%" -am -Dspring-boot.run.profiles=%PROFILES% -Dspring-boot.run.arguments=%EXTRA_ARGS%
endlocal
