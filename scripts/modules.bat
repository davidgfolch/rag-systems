@echo off
setlocal enabledelayedexpansion
REM ===== RAG Systems Module Discovery Helper (Windows) =====
REM Usage: modules.bat list [--runnable]
REM        modules.bat check [--runnable] ^<module^>
REM   list             - Print every Maven module under apps\ (directories with pom.xml)
REM   list --runnable - Print only modules declaring spring-boot-maven-plugin
REM   check ^<module^>  - errorlevel 0 when ^<module^> is valid, 1 otherwise
REM Discovery is filesystem-based, so adding a module under apps\ needs no list update.

set "ROOT=%~dp0.."
for %%I in ("%ROOT%") do set "ROOT=%%~fI"
set "RUNNABLE_MARKER=spring-boot-maven-plugin"
set "CMD=%~1"
set "ARG2=%~2"
set "ARG3=%~3"
if "%CMD%"=="" set "CMD=list"

if "%CMD%"=="list" goto :list
if "%CMD%"=="--list" goto :list
if "%CMD%"=="check" goto :check
goto :usage

:list
set "RUNNABLE="
if /I "%ARG2%"=="--runnable" set "RUNNABLE=1"
if /I "%ARG2%"=="runnable" set "RUNNABLE=1"
if /I "%ARG3%"=="--runnable" set "RUNNABLE=1"
if /I "%ARG3%"=="runnable" set "RUNNABLE=1"
for /d %%D in ("%ROOT%\apps\*") do (
    if exist "%%D\pom.xml" (
        if defined RUNNABLE (
            findstr /c:"%RUNNABLE_MARKER%" "%%D\pom.xml" >nul 2>nul && echo %%~nxD
        ) else (
            echo %%~nxD
        )
    )
)
endlocal
exit /b 0

:check
set "RUNNABLE="
set "MODULE=%ARG2%"
if /I "%ARG2%"=="--runnable" set "MODULE=%ARG3%"
if /I "%ARG2%"=="runnable" set "MODULE=%ARG3%"
if /I "%ARG2%"=="--runnable" set "RUNNABLE=1"
if /I "%ARG2%"=="runnable" set "RUNNABLE=1"
if /I "%ARG3%"=="--runnable" set "RUNNABLE=1"
if /I "%ARG3%"=="runnable" set "RUNNABLE=1"
if "%MODULE%"=="" goto :usage
if not exist "%ROOT%\apps\%MODULE%\pom.xml" (
    endlocal
    exit /b 1
)
if not defined RUNNABLE (
    endlocal
    exit /b 0
)
findstr /c:"%RUNNABLE_MARKER%" "%ROOT%\apps\%MODULE%\pom.xml" >nul 2>nul
if errorlevel 1 (
    endlocal
    exit /b 1
)
endlocal
exit /b 0

:usage
echo Usage: modules.bat list [--runnable] ^| check [--runnable] ^<module^>
echo   list             - Print every Maven module under apps\ (directories with pom.xml)
echo   list --runnable - Print only modules declaring spring-boot-maven-plugin
echo   check ^<module^>  - errorlevel 0 when the module is valid, 1 otherwise
endlocal
exit /b 2
