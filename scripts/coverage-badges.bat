@echo off
REM ===== RAG Systems Coverage Badges Generator (Windows) =====
REM Usage: coverage-badges.bat
REM   Generates per-module coverage badges from JaCoCo CSV reports
REM   and inserts them into README.md between markers.

setlocal

cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0coverage-badges.ps1"

endlocal