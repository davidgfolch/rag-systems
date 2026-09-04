@echo off
setlocal enabledelayedexpansion
REM ===== RAG Systems Stop-gate hook (Windows) =====
REM Enforces the SDLC rule "always execute all tests after implementation".
REM Runs on the Stop event. Inspects the working tree.
REM Emits an instructive stopReason when JAVA code was modified on a feature branch,
REM reminding the agent to run tests before done.
REM Scope: only .java changes trigger the test reminder. Docs/skills/rules/config
REM changes (no .java, no build/script/pom changes) skip the test gate per
REM docs/process/asdlc.md Rule 1, so they are intentionally silent here.
REM Returns 0 (continue:true) so normal workflows are never hard-blocked; the
REM hard gate lives in `dev auto-merge` (merges only green PRs) and the rules.

set "ROOT=%~1"
if "%ROOT%"=="" set "ROOT=%CD%"
cd /d "%ROOT%" || exit /b 0

set "BRANCH="
for /f "usebackq delims=" %%B in (`git rev-parse --abbrev-ref HEAD 2^>nul`) do set "BRANCH=%%B"

set "IS_FEATURE=0"
if defined BRANCH (
    for /f "tokens=1 delims=/" %%P in ("%BRANCH%") do if "%%P"=="feat" set "IS_FEATURE=1"
)

set "MODIFIED=0"
set "MODIFIED_JAVA=0"
for /f "usebackq delims=" %%F in (`git status --porcelain 2^>nul`) do (
    set "MODIFIED=1"
    echo %%F | findstr /r /c:"\.java" >nul && set "MODIFIED_JAVA=1"
)

if not "!IS_FEATURE!"=="1" (
    if "!MODIFIED!"=="1" (
        echo {"continue":true,"stopReason":"SDLC gate: changes detected. Commit and push them: dev commit "message" then dev push (never on main: use a feature clone)."}
    ) else (
        echo {"continue":true}
    )
) else (
    if "!MODIFIED_JAVA!"=="1" (
        echo {"continue":true,"stopReason":"SDLC gate: Java source files were modified on a feature branch. Before finishing, run the full test suite: scripts/test.bat (Windows) or scripts/test.sh (Unix), then `dev check [module]` (tests + SonarQube). Then commit and push: dev commit "message" then dev push. Do not open a PR or merge until the tests pass."}
    ) else if "!MODIFIED!"=="1" (
        echo {"continue":true,"stopReason":"SDLC gate: working tree has non-Java (docs/rules/config) changes on a feature branch. Skip tests+SonarQube per asdlc.md Rule 1, but commit and push: dev commit "message" then dev push."}
    ) else (
        echo {"continue":true}
    )
)

exit /b 0
