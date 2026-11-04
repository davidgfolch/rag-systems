@echo off
setlocal
REM ===== SonarQube Bootstrap: set admin password + generate token (Windows) =====
REM Called automatically by sonar.bat after SonarQube is ready.
REM Usage: sonar-pw.bat [--gate-only]
REM   --gate-only  Only (re)apply the quality gate; skip password/token bootstrap.
REM Idempotent: uses .sonarqube\admin_pw_set marker to skip on subsequent runs.
REM Env: SONAR_ADMIN_PASSWORD (from .env.secrets or shell), SONAR_TOKEN (from .env.secrets or shell).

set "ROOT=%~dp0.."
cd /d "%ROOT%"

set "HOST=http://localhost:9000"
set "MARKER=.sonarqube\admin_pw_set"
set "SECRETS_FILE=.env.secrets"
set "TOKEN_NAME=rag-local-ci"
set "AUTH_JSON=%TEMP%\sonar_auth.json"
set "TOKEN_JSON=%TEMP%\sonar_token.json"
set "TOKEN_OUT=%TEMP%\sonar_token_value.txt"
set "PW_CALC=%TEMP%\sonar_pw.txt"
set "GATE_ONLY=0"
if "%~1"=="--gate-only" set "GATE_ONLY=1"

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

REM Strengthen the password to SonarQube's policy: 12+ chars, upper/lower/digit/special.
set "ORIG_PW=%NEW_PW%"
powershell -NoProfile -Command "$p=$env:NEW_PW; if($p -notmatch '[A-Z]'){$p+='A'}; if($p -notmatch '[a-z]'){$p+='a'}; if($p -notmatch '[0-9]'){$p+='1'}; if($p -notmatch '[^A-Za-z0-9]'){$p+='-'}; while($p.Length -lt 12){$p+='x9'}; if($p -ne $env:NEW_PW -and (Test-Path '%SECRETS_FILE%')){ $lines=Get-Content '%SECRETS_FILE%'; $lines | ForEach-Object { if ($_ -match '^SONAR_ADMIN_PASSWORD=') { 'SONAR_ADMIN_PASSWORD=' + $p } else { $_ } } | Set-Content '%SECRETS_FILE%' }; Set-Content '%PW_CALC%' $p"
set "NEW_PW="
if exist "%PW_CALC%" set /p NEW_PW=<"%PW_CALC%"
if "%NEW_PW%"=="" set "NEW_PW=admin"
if not "%NEW_PW%"=="%ORIG_PW%" echo Strengthening SONAR_ADMIN_PASSWORD to satisfy the SonarQube policy.

if "%GATE_ONLY%"=="1" goto :configure_qg

if exist "%MARKER%" (
    echo SonarQube admin password already configured.
    goto :configure_qg
)

echo Waiting for SonarQube to be ready...
set "READY=0"
for /L %%I in (1,1,30) do (
    if "%READY%"=="0" (
        curl -s "%HOST%/api/system/status" >nul 2>&1
        if not errorlevel 1 set "READY=1"
    )
    if "%READY%"=="0" timeout /t 5 /nobreak >nul
)

if "%READY%"=="0" (
    echo Error: SonarQube did not become ready within 150s.
    exit /b 1
)

call :check_auth
if "%AUTH_OK%"=="1" (
    echo Admin password already set to SONAR_ADMIN_PASSWORD.
    goto :generate_token
)

echo Setting admin password...
set "HTTP_CODE="
curl -s -o nul -w "%%{http_code}" -u "admin:admin" -X POST "%HOST%/api/users/change_password" --data-urlencode "login=admin" --data-urlencode "previousPassword=admin" --data-urlencode "password=%NEW_PW%" > "%TEMP%\sonar_http.txt" 2>nul
if exist "%TEMP%\sonar_http.txt" set /p HTTP_CODE=<"%TEMP%\sonar_http.txt"

if "%HTTP_CODE%"=="200" goto :password_set
call :check_auth
if "%AUTH_OK%"=="0" (
    echo Error: could not set admin password ^(HTTP %HTTP_CODE%^).
    echo Set SONAR_ADMIN_PASSWORD in %SECRETS_FILE% to the current admin password and retry.
    exit /b 1
)

:password_set
echo Admin password set.

:generate_token
echo Generating analysis token...
curl -s -u "admin:%NEW_PW%" -X POST "%HOST%/api/user_tokens/generate" --data-urlencode "name=%TOKEN_NAME%" > "%TOKEN_JSON%" 2>nul
set "TOKEN_VALUE="
call :read_token

if "%TOKEN_VALUE%"=="" (
    curl -s -o nul -u "admin:%NEW_PW%" -X POST "%HOST%/api/user_tokens/revoke" --data-urlencode "name=%TOKEN_NAME%" 2>nul
    curl -s -u "admin:%NEW_PW%" -X POST "%HOST%/api/user_tokens/generate" --data-urlencode "name=%TOKEN_NAME%" > "%TOKEN_JSON%" 2>nul
    call :read_token
)

if "%TOKEN_VALUE%"=="" (
    echo Warning: could not generate token.
    echo Check SonarQube logs or generate manually at %HOST%
    goto :configure_qg
)

echo Token generated: %TOKEN_VALUE%

REM Save token to .env.secrets (update the existing line, or append it).
if exist "%SECRETS_FILE%" (
    set "NEW_TOKEN=%TOKEN_VALUE%"
    powershell -NoProfile -Command "$t=$env:NEW_TOKEN; $lines=Get-Content '%SECRETS_FILE%'; if ($lines -match '^SONAR_TOKEN=') { $lines | ForEach-Object { if ($_ -match '^SONAR_TOKEN=') { 'SONAR_TOKEN=' + $t } else { $_ } } | Set-Content '%SECRETS_FILE%' } else { Add-Content '%SECRETS_FILE%' ('SONAR_TOKEN=' + $t) }"
) else (
    echo SONAR_ADMIN_PASSWORD=%NEW_PW%> "%SECRETS_FILE%"
    echo SONAR_TOKEN=%TOKEN_VALUE%>> "%SECRETS_FILE%"
)

:configure_qg
REM --- Quality Gate: enforce 90% overall coverage ---
set "GATE_NAME=RAG 90%% Coverage"
set "GATE_CONDITION_METRIC=coverage"
set "GATE_CONDITION_OP=LT"
set "GATE_CONDITION_VALUE=90"
set "PROJECT_KEY=com.rag:rag-systems"

echo Configuring quality gate ^(>= 90%% coverage^)...
REM Query parameters are required by these endpoints on SonarQube 26.8; a form
REM body returns HTTP 500.
set "QG_HTTP="
curl -s -o nul -w "%%{http_code}" -u "admin:%NEW_PW%" -X POST "%HOST%/api/qualitygates/create" -G --data-urlencode "name=%GATE_NAME%" > "%TEMP%\sonar_qg.txt" 2>nul
if exist "%TEMP%\sonar_qg.txt" set /p QG_HTTP=<"%TEMP%\sonar_qg.txt"

if "%QG_HTTP%"=="200" goto :create_qg_condition
REM A duplicate name returns 400; treat an existing gate as ready.
set "GATES_JSON=%TEMP%\sonar_gates.json"
curl -s -u "admin:%NEW_PW%" "%HOST%/api/qualitygates/list" > "%GATES_JSON%" 2>nul
set "GATE_EXISTS=0"
powershell -NoProfile -Command "$g=Get-Content $env:GATES_JSON -Raw | ConvertFrom-Json; if ($g.qualitygates | Where-Object { $_.name -eq $env:GATE_NAME }) { exit 0 } else { exit 1 }" >nul 2>&1
if not errorlevel 1 set "GATE_EXISTS=1"
if "%GATE_EXISTS%"=="1" goto :create_qg_condition
echo Warning: could not create quality gate ^(HTTP %QG_HTTP%^).
goto :skip_qg

:create_qg_condition
curl -s -o nul -u "admin:%NEW_PW%" -X POST "%HOST%/api/qualitygates/create_condition" -G --data-urlencode "gateName=%GATE_NAME%" --data-urlencode "metric=%GATE_CONDITION_METRIC%" --data-urlencode "op=%GATE_CONDITION_OP%" --data-urlencode "error=%GATE_CONDITION_VALUE%" 2>nul
curl -s -o nul -u "admin:%NEW_PW%" -X POST "%HOST%/api/qualitygates/select" -G --data-urlencode "gateName=%GATE_NAME%" --data-urlencode "projectKey=%PROJECT_KEY%" 2>nul
echo Quality gate '%GATE_NAME%' configured ^(>= %GATE_CONDITION_VALUE%%% coverage^).

:skip_qg
if "%GATE_ONLY%"=="1" goto :done

REM Write marker
if not exist ".sonarqube" mkdir ".sonarqube"
type nul > "%MARKER%"

:done
echo SonarQube bootstrap complete. Token saved to %SECRETS_FILE%.
endlocal
exit /b 0

:check_auth
set "AUTH_OK=0"
curl -s -u "admin:%NEW_PW%" "%HOST%/api/authentication/validate" > "%AUTH_JSON%" 2>nul
powershell -NoProfile -Command "$j=Get-Content $env:AUTH_JSON -Raw | ConvertFrom-Json; if ($j.valid) { exit 0 } else { exit 1 }" >nul 2>&1
if not errorlevel 1 set "AUTH_OK=1"
goto :eof

:read_token
set "TOKEN_VALUE="
if exist "%TOKEN_OUT%" del "%TOKEN_OUT%" >nul 2>&1
powershell -NoProfile -Command "try { $j=ConvertFrom-Json (Get-Content $env:TOKEN_JSON -Raw); Set-Content $env:TOKEN_OUT $j.token } catch { Set-Content $env:TOKEN_OUT '' }" >nul 2>&1
if exist "%TOKEN_OUT%" set /p TOKEN_VALUE=<"%TOKEN_OUT%"
goto :eof
