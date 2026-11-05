@echo off
setlocal
REM ===== RAG Systems Parallel Development Workflow (Windows) =====
REM Usage: dev.bat <command> [args]
REM   dev.bat new <name>       - Clone to ..\rag-systems-<name>, branch feat/<name>, copy Sonar token
REM   dev.bat checkout <br>    - Switch branch (current dir)
REM   dev.bat test [module]    - Run tests (delegates to test.bat)
REM   dev.bat sonar [token]    - Run SonarQube scan (delegates to sonar.bat)
REM   dev.bat check [module]   - Tests, then SonarQube scan if tests pass
REM   dev.bat commit "msg"     - git add -A && git commit
REM   dev.bat push             - git push -u origin <branch>
REM   dev.bat pr [title]       - Create PR against main
REM   dev.bat auto-merge [-k]  - Wait for green checks, auto-merge, auto-resolve conflicts, delete remote branch + clone (keep clone with -k)
REM   dev.bat merge [-k]       - Alias of auto-merge
REM   dev.bat cleanup <name>   - Delete ..\rag-systems-<name>
REM   dev.bat status           - Branch, dirty state, ahead/behind

set "ROOT=%~dp0.."
set "CMD=%~1"

if "%CMD%"=="new"      goto :new
if "%CMD%"=="checkout" goto :checkout
if "%CMD%"=="test"     goto :test
if "%CMD%"=="sonar"    goto :sonar
if "%CMD%"=="check"    goto :check
if "%CMD%"=="commit"   goto :commit
if "%CMD%"=="push"     goto :push
if "%CMD%"=="pr"       goto :pr
if "%CMD%"=="merge"    goto :merge
if "%CMD%"=="auto-merge" goto :merge
if "%CMD%"=="cleanup"  goto :cleanup
if "%CMD%"=="status"   goto :status
goto :usage

:new
set "NAME=%~2"
if "%NAME%"=="" goto :usage
set "PARENT=%ROOT%\.."
set "CLONE=%PARENT%\rag-systems-%NAME%"
set "BRANCH=feat/%NAME%"
for /f "delims=" %%U in ('git remote get-url origin') do set "ORIGIN_URL=%%U"
if exist "%CLONE%" (
    echo Clone already exists: %CLONE%
    echo Reusing it. Ensure its branch is "%BRANCH%".
) else (
    echo Cloning %ORIGIN_URL% to %CLONE%...
    git clone "%ORIGIN_URL%" "%CLONE%" 2>nul
    if not exist "%CLONE%" (
        echo Error: clone failed. Check the remote origin URL.
        exit /b 1
    )
)
cd /d "%CLONE%"
git fetch origin >nul 2>&1
git checkout -b "%BRANCH%" origin/main 2>nul || git checkout "%BRANCH%" 2>nul || (
    echo Error: could not set branch %BRANCH%.
    exit /b 1
)
call :propagate_token "%ROOT%"
call :codegraph_index "%CLONE%"
echo.
echo Clone ready: %CLONE%
echo Branch: %BRANCH%
exit /b 0

:propagate_token
REM Copy SONAR_TOKEN (and admin pw) from the original workspace into this clone
set "SRC=%ROOT%\.env.secrets"
if exist "%SRC%" (
    if not exist ".env.secrets" copy "%SRC%" ".env.secrets" >nul
    echo Sonar token propagated into .env.secrets
) else (
    echo Note: no .env.secrets in the original. SonarQube token will be auto-generated on first scan.
)
goto :eof

:codegraph_index
where codegraph >nul 2>nul
if errorlevel 1 (
    echo CodeGraph CLI not found; skipping clone index build.
    goto :eof
)
echo Building CodeGraph index in %~1...
pushd "%~1"
call codegraph init -y
set "CGERR=%errorlevel%"
popd
if not "%CGERR%"=="0" echo Warning: CodeGraph index build failed for %~1.
goto :eof

:stop_codegraph_daemon
REM Stop the CodeGraph daemon recorded in %~1 (a daemon.pid JSON file), but only
REM when the pid maps to a real CodeGraph process. Stale/recycled pids are skipped.
if not exist "%~1" exit /b 0
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "$json = Get-Content -Raw '%~1' | ConvertFrom-Json; $p = $json.pid; if ($p) { $proc = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $p) -ErrorAction SilentlyContinue; if ($proc -and $proc.ExecutablePath -like '*codegraph*') { Stop-Process -Id $p -Force -ErrorAction SilentlyContinue; Write-Output $p } }"`) do (
    echo Stopped CodeGraph daemon %%P ^(tracked by %~1^)
)
exit /b 0

:release_codegraph_locks
REM Release CodeGraph locks on the clone %~1. Only its own daemon can lock its
REM index, so the main-workspace daemon is left alone unless the delete fails.
set "RC_TARGET=%~1"
if not exist "%RC_TARGET%\.codegraph\codegraph.db" (
    echo No CodeGraph index in %RC_TARGET% - nothing to release.
    exit /b 0
)
call :stop_codegraph_daemon "%RC_TARGET%\.codegraph\daemon.pid"
exit /b 0

:delete_clone
REM Delete clone %~1. First release its own CodeGraph daemon, then delete. If the
REM directory survives (main-workspace daemon holds its DB open), release that
REM daemon as well and retry once.
set "DC_TARGET=%~1"
call :release_codegraph_locks "%DC_TARGET%"
cd /d "%DC_TARGET%\.."
echo Deleting clone %DC_TARGET%...
rmdir /s /q "%DC_TARGET%"
if exist "%DC_TARGET%" (
    echo Delete blocked; releasing main-workspace CodeGraph daemon and retrying...
    call :stop_codegraph_daemon "%ROOT%\..\rag-systems\.codegraph\daemon.pid"
    rmdir /s /q "%DC_TARGET%"
)
if exist "%DC_TARGET%" (
    echo Error: could not delete %DC_TARGET% even after releasing CodeGraph daemons.
    exit /b 1
)
echo Clone deleted.
exit /b 0

:checkout
if "%~2"=="" goto :usage
git checkout "%~2"
exit /b %errorlevel%

:test
set "ARGS=%*"
for /f "tokens=1,* delims= " %%A in ("%ARGS%") do set "REST=%%B"
cd /d "%ROOT%"
call scripts\test.bat %REST%
exit /b %errorlevel%

:sonar
set "ARGS=%*"
for /f "tokens=1,* delims= " %%A in ("%ARGS%") do set "REST=%%B"
cd /d "%ROOT%"
call scripts\sonar.bat %REST%
exit /b %errorlevel%

:check
set "ARGS=%*"
for /f "tokens=1,* delims= " %%A in ("%ARGS%") do set "REST=%%B"
cd /d "%ROOT%"
echo Running tests...
call scripts\test.bat %REST%
if not errorlevel 1 (
    echo Tests passed. Running SonarQube scan...
    call :token_or_regenerate
) else (
    echo Tests failed. Skipping SonarQube.
)
exit /b %errorlevel%

:token_or_regenerate
set "TOKEN="
if not "%SONAR_TOKEN%"=="" set "TOKEN=%SONAR_TOKEN%"
if "%TOKEN%"=="" (
    if exist ".env.secrets" (
        for /f "usebackq tokens=1,* delims==" %%A in (".env.secrets") do (
            if "%%A"=="SONAR_TOKEN" if not "%%B"=="" set "TOKEN=%%B"
        )
    )
)
if "%TOKEN%"=="" (
    echo No SonarQube token found; bootstrapping and scanning via up-scan...
    call scripts\sonar.bat up-scan
) else (
    call scripts\sonar.bat scan "%TOKEN%"
)
goto :eof

:commit
if "%~2"=="" goto :usage
git add -A
if not errorlevel 1 git commit -m "%~2"
exit /b %errorlevel%

:push
for /f "delims=" %%B in ('git rev-parse --abbrev-ref HEAD') do set "CURBRANCH=%%B"
if "%CURBRANCH%"=="main" (
    echo Refusing to push main directly. Use a feature branch.
    exit /b 1
)
git push -u origin "%CURBRANCH%"
exit /b %errorlevel%

:pr
for /f "delims=" %%B in ('git rev-parse --abbrev-ref HEAD') do set "CURBRANCH=%%B"
set "PR_TITLE=%~2"
if "%PR_TITLE%"=="" (
    for /f "delims=" %%M in ('git log -1 --pretty=%%s') do set "PR_TITLE=%%M"
)
if "%CURBRANCH%"=="main" (
    echo Refusing to open a PR from main. Use a feature branch.
    exit /b 1
)
gh pr create --base main --head "%CURBRANCH%" --title "%PR_TITLE%" --body "Automated PR from the dev workflow for branch %CURBRANCH%."
exit /b %errorlevel%

:merge
set "KEEP=%~2"
for /f "delims=" %%B in ('git rev-parse --abbrev-ref HEAD') do set "CURBRANCH=%%B"
if "%CURBRANCH%"=="main" (
    echo Cannot merge from main. Run inside a feature clone.
    exit /b 1
)
where gh >nul 2>nul
if errorlevel 1 (
    echo Error: 'gh' CLI is required. Install GitHub CLI and authenticate.
    exit /b 1
)
for /f "delims=" %%N in ('gh pr list --head "%CURBRANCH%" --json number --jq ".[0].number"') do set "PRNUM=%%N"
if "%PRNUM%"=="" (
    echo Error: no open PR found for branch %CURBRANCH%.
    exit /b 1
)
echo Resolving PR #%PRNUM% state...
echo Waiting for PR checks to appear...
set "CHECKS=0"
call :wait_for_checks
if "%CHECKS%"=="0" (
    echo No checks reported for PR #%PRNUM%. Clone kept for inspection.
    exit /b 1
)
echo Waiting for PR checks to be green...
gh pr checks "%PRNUM%" --watch
if errorlevel 1 (
    echo PR checks did not all pass. Clone kept for inspection.
    exit /b 1
)
gh pr view "%PRNUM%" --json statusCheckRollup > "%TEMP%\rag-pr-checks.json"
findstr /C:"\"conclusion\":\"FAILURE\"" /C:"\"conclusion\":\"CANCELLED\"" /C:"\"conclusion\":\"TIMED_OUT\"" /C:"\"conclusion\":\"ACTION_REQUIRED\"" /C:"\"state\":\"ERROR\"" "%TEMP%\rag-pr-checks.json" >nul 2>nul
if not errorlevel 1 (
    del "%TEMP%\rag-pr-checks.json" >nul 2>nul
    echo PR has failing or cancelled checks. Clone kept for inspection.
    exit /b 1
)
del "%TEMP%\rag-pr-checks.json" >nul 2>nul
for /f "delims=" %%M in ('gh pr view "%PRNUM%" --json mergeable --jq ".mergeable"') do set "MERGEABLE=%%M"
if not "%MERGEABLE%"=="MERGEABLE" (
    echo PR is not mergeable yet; attempting to auto-resolve conflicts...
    git fetch origin main >nul 2>&1
    git rebase origin/main
    if not errorlevel 1 (
        echo Rebase succeeded; conflict resolved automatically.
        git push --force-with-lease origin "%CURBRANCH%"
        if errorlevel 1 (
            echo Push after rebase failed. Clone kept.
            exit /b 1
        )
    ) else (
        git rebase --abort >nul 2>&1
        echo Error: could not auto-resolve conflicts ^(rebase failed^). Resolve manually, then re-run auto-merge. Clone kept.
        exit /b 1
    )
)
echo Merging PR #%PRNUM% (auto-merge once required checks pass)...
gh pr merge "%PRNUM%" --auto --squash --delete-branch
if errorlevel 1 (
    echo PR merge failed. Clone kept for inspection.
    exit /b 1
)
echo Auto-merge enabled; waiting for the merge...
call :wait_for_merge
if "%STATE%"=="CLOSED" (
    echo PR closed without merging. Clone kept for inspection.
    exit /b 1
)
if not "%STATE%"=="MERGED" (
    echo Timed out waiting for the merge. Clone kept for inspection.
    exit /b 1
)
echo PR merged.
if /I "%KEEP%"=="-k" (
    echo Keeping clone.
) else (
    call :delete_merged_clone
)
exit /b 0

:wait_for_checks
set /a CTRIES=0
:wait_for_checks_loop
for /f "delims=" %%C in ('gh pr view "%PRNUM%" --json statusCheckRollup --jq ".statusCheckRollup | length"') do set "CHECKS=%%C"
if not "%CHECKS%"=="0" exit /b 0
set /a CTRIES+=1
if %CTRIES% GEQ 30 exit /b 0
timeout /t 10 /nobreak >nul
goto :wait_for_checks_loop

:wait_for_merge
set "STATE="
set /a MTRIES=0
:wait_for_merge_loop
for /f "delims=" %%S in ('gh pr view "%PRNUM%" --json state --jq ".state"') do set "STATE=%%S"
if "%STATE%"=="MERGED" exit /b 0
if "%STATE%"=="CLOSED" exit /b 0
set /a MTRIES+=1
if %MTRIES% GEQ 90 exit /b 0
timeout /t 10 /nobreak >nul
goto :wait_for_merge_loop

:delete_merged_clone
set "SUFFIX=%CURBRANCH:feat/=%"
set "TARGET=%ROOT%\..\rag-systems-%SUFFIX%"
if exist "%TARGET%" call :delete_clone "%TARGET%"
exit /b 0

:cleanup
set "NAME=%~2"
if "%NAME%"=="" goto :usage
set "TARGET=%ROOT%\..\rag-systems-%NAME%"
if exist "%TARGET%" (
    call :delete_clone "%TARGET%"
) else (
    echo Not found: %TARGET%
)
exit /b 0

:status
for /f "delims=" %%B in ('git rev-parse --abbrev-ref HEAD') do set "CURBRANCH=%%B"
echo Branch: %CURBRANCH%
echo.
git status --short
echo.
git rev-list --left-right --count origin/main...HEAD
exit /b 0

:usage
echo Usage: dev.bat ^<command^> [args]
echo   new ^<name^>  checkout ^<br^>  test [module]  sonar [token]
echo   check [module]  commit "msg"  push  pr [title]
echo   auto-merge [-k]  merge [-k]  cleanup ^<name^>  status
exit /b 1
