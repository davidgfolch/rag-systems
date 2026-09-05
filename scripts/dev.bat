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

:checkout
if "%~2"=="" goto :usage
git checkout "%~2"
exit /b %errorlevel%

:test
shift
cd /d "%ROOT%"
call scripts\test.bat %*
exit /b %errorlevel%

:sonar
shift
cd /d "%ROOT%"
call scripts\sonar.bat %*
exit /b %errorlevel%

:check
shift
cd /d "%ROOT%"
echo Running tests...
call scripts\test.bat %*
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
echo Waiting for PR checks to be green...
gh pr checks "%PRNUM%" --watch
if errorlevel 1 (
    echo PR checks did not all pass. Clone kept for inspection.
    exit /b 1
)
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
echo Merging PR #%PRNUM%...
gh pr merge "%PRNUM%" --squash --delete-branch
if not errorlevel 1 (
    echo PR merged.
    if /I "%KEEP%"=="-k" (
        echo Keeping clone.
    ) else (
        set "SUFFIX=%CURBRANCH:feat/=%"
        set "TARGET=%ROOT%\..\rag-systems-%SUFFIX%"
        if exist "%TARGET%" (
            cd /d "%TARGET%\.."
            echo Deleting clone %TARGET%...
            rmdir /s /q "%TARGET%"
            echo Clone deleted.
        )
    )
) else (
    echo PR merge failed. Clone kept for inspection.
)
exit /b %errorlevel%

:cleanup
set "NAME=%~2"
if "%NAME%"=="" goto :usage
set "TARGET=%ROOT%\..\rag-systems-%NAME%"
if exist "%TARGET%" (
    echo Deleting %TARGET%...
    rmdir /s /q "%TARGET%"
    echo Deleted.
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
