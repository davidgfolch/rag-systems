@echo off
setlocal
REM ===== SonarQube Bootstrap: set admin password + generate token (Windows) =====
REM Called automatically by sonar.bat after SonarQube is ready.
REM Idempotent: uses .sonarqube\admin_pw_set marker to skip on subsequent runs.
REM Env: SONAR_ADMIN_PASSWORD (from .env.secrets or shell), SONAR_TOKEN (from .env.secrets or shell).

set "ROOT=%~dp0.."
cd /d "%ROOT%"

set "HOST=http://localhost:9000"
set "MARKER=.sonarqube\admin_pw_set"
set "SECRETS_FILE=.env.secrets"
set "TOKEN_NAME=rag-local-ci"

REM Load .env.secrets if present
if exist "%SECRETS_FILE%" (
    for /f "usebackq tokens=1,* delims==" %%A in ("%SECRETS_FILE%") do (
        if not "%%B"=="" if not "%%A"=="" (
            if "%%A"=="SONAR_ADMIN_PASSWORD" set "NEW_PW=%%B"
            if "%%A"=="SONAR_TOKEN" set "SONAR_TOKEN=%%B"
        )
    )
)

if "%NEW_PW%"=="" set "NEW_PW=admin"

REM Check marker
if exist "%MARKER%" (
    echo SonarQube admin password already configured.
    exit /b 0
)

echo Waiting for SonarQube to be ready...
set "READY=0"
for /L %%I in (1,1,30) do (
    if "%READY%"=="0" (
        curl -sf "%HOST%/api/system/status" >nul 2>&1
        if not errorlevel 1 set "READY=1"
    )
    if "%READY%"=="0" timeout /t 5 /nobreak >nul
)

if "%READY%"=="0" (
    echo Error: SonarQube did not become ready within 150s.
    exit /b 1
)

echo Setting admin password...
curl -sf -o nul -w "%%{http_code}" -u "admin:admin" -X POST "%HOST%/api/authentication/change_password" -d "login=admin" -d "previousPassword=admin" -d "password=%NEW_PW%" > "%TEMP%\sonar_http.txt" 2>&1
set /p HTTP_CODE=<"%TEMP%\sonar_http.txt"

if not "%HTTP_CODE%"=="200" (
    echo Warning: could not set admin password ^(HTTP %HTTP_CODE%^).
    echo The server may have already been bootstrapped.
    exit /b 0
)

echo Admin password set.

echo Generating analysis token...
curl -sf -u "admin:%NEW_PW%" -X POST "%HOST%/api/user_tokens/generate" -d "name=%TOKEN_NAME%" > "%TEMP%\sonar_token.json" 2>&1
for /f "tokens=2 delims=:, " %%T in ('findstr /c:"token" "%TEMP%\sonar_token.json"') do set "TOKEN_VALUE=%%~T"

REM Remove surrounding quotes from token
set "TOKEN_VALUE=%TOKEN_VALUE:"=%"

if "%TOKEN_VALUE%"=="" (
    echo Warning: could not generate token.
    echo Check SonarQube logs or generate manually at %HOST%
    exit /b 0
)

echo Token generated: %TOKEN_VALUE%

REM Save token to .env.secrets
if exist "%SECRETS_FILE%" (
    powershell -NoProfile -Command "(Get-Content '%SECRETS_FILE%') -replace '^SONAR_TOKEN=.*','SONAR_TOKEN=%TOKEN_VALUE%' | Set-Content '%SECRETS_FILE%'"
) else (
    echo SONAR_ADMIN_PASSWORD=%NEW_PW%> "%SECRETS_FILE%"
    echo SONAR_TOKEN=%TOKEN_VALUE%>> "%SECRETS_FILE%"
)

REM Write marker
if not exist ".sonarqube" mkdir ".sonarqube"
type nul > "%MARKER%"

echo SonarQube bootstrap complete. Token saved to %SECRETS_FILE%.
endlocal
exit /b 0
