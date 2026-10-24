@echo off
setlocal EnableExtensions
set "HOST_JAVA_HOME=%JAVA_HOME%"
if exist "%~dp0env.cmd" call "%~dp0env.cmd"
if not defined JAVA_HOME if defined HOST_JAVA_HOME set "JAVA_HOME=%HOST_JAVA_HOME%"
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
set "PIDFILE=%ROOT%\.run\cas.pid"
set "LOGDIR=%ROOT%\logs"

if not defined JAVA_HOME goto no_java
if not exist "%JAVA_HOME%\bin\java.exe" goto no_java
"%JAVA_HOME%\bin\java.exe" -XshowSettings:properties -version 2>&1 | findstr /C:"java.version = 21" >nul
if errorlevel 1 goto bad_java
if not exist "%ROOT%\target\cas.war" goto no_war
if not defined CAS_DB_PASSWORD goto no_db

netstat -ano | findstr /C:":8080" | findstr /C:"LISTENING" >nul
if not errorlevel 1 goto port_used
netstat -ano | findstr /C:":8083" | findstr /C:"LISTENING" >nul
if not errorlevel 1 goto port_used

if not exist "%LOGDIR%" mkdir "%LOGDIR%"
if not exist "%ROOT%\.run" mkdir "%ROOT%\.run"
if not defined CAS_SERVICE_DIR set "CAS_SERVICE_DIR=%ROOT%\etc\cas\services"
if not defined CAS_ADMIN_TOKEN echo [cas] warning: CAS_ADMIN_TOKEN is empty; control plane will return 503.

echo [cas] starting...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$p = Start-Process -FilePath '%JAVA_HOME%\bin\java.exe' -ArgumentList '-jar','%ROOT%\target\cas.war' -WorkingDirectory '%ROOT%' -RedirectStandardOutput '%LOGDIR%\cas.out.log' -RedirectStandardError '%LOGDIR%\cas.err.log' -PassThru -WindowStyle Hidden; Set-Content -Path '%PIDFILE%' -Value $p.Id"
if errorlevel 1 goto start_failed

set /a WAIT=0
:wait
rem IMPORTANT: probe /p3/serviceValidate, NOT /cas/login. The browser guard 302s
rem HTTP /cas/login to HTTPS, so a ==200 check on it never passes and the probe
rem would kill a healthy CAS after 60 retries. serviceValidate always returns 200.
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8080/cas/p3/serviceValidate' -TimeoutSec 2 -MaximumRedirection 0 | Out-Null; exit 0 } catch { if ($_.Exception.Response) { exit 0 } else { exit 1 } }" >nul 2>&1
if not errorlevel 1 goto wait_https
set /a WAIT+=1
if %WAIT% GEQ 60 goto timeout
"%SystemRoot%\System32\ping.exe" -n 2 127.0.0.1 >nul
goto wait

:wait_https
rem HTTPS entry (8083) readiness check; warn only, do not block (CAS may run HTTP-only when keystore missing).
rem Accept any HTTP response (incl. cert-error paths) as long as TCP+TLS answered; -SkipCertificateCheck ok on PS7 only.
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { Invoke-WebRequest -UseBasicParsing -Uri 'https://127.0.0.1:8083/cas/login' -TimeoutSec 3 | Out-Null; exit 0 } catch { if ($_.Exception.Response -or $_.Exception.Message -match 'remote certificate') { exit 0 } else { exit 1 } }" >nul 2>&1
if errorlevel 1 echo [cas] warning: https://127.0.0.1:8083 not reachable - CAS running in HTTP-only mode (keystore missing?)

:ready
echo [cas] ready: http://127.0.0.1:8080/cas  (browser: https://localhost:8083/cas)
echo [cas] logs: logs\cas.out.log / logs\cas.err.log
endlocal & exit /b 0

:timeout
echo [cas] start timeout. See logs\cas.out.log and logs\cas.err.log
call "%~dp0stop-cas.cmd" >nul 2>&1
endlocal & exit /b 1

:no_java
echo [cas] JAVA_HOME is not set or invalid. Set JDK 21 in the current environment or in bin\env.cmd.
endlocal & exit /b 1

:bad_java
echo [cas] JDK 21 is required. Current JAVA_HOME=%JAVA_HOME%
endlocal & exit /b 1

:no_war
echo [cas] target\cas.war not found. Run bin\build-cas.cmd first.
endlocal & exit /b 1

:no_db
echo [cas] CAS_DB_PASSWORD is not set. Edit bin\env.cmd first.
endlocal & exit /b 1

:port_used
echo [cas] port 8080 or 8083 is already in use. Run bin\stop-cas.cmd first.
endlocal & exit /b 1

:start_failed
echo [cas] failed to start java process.
endlocal & exit /b 1
