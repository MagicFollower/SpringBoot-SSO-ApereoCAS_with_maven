@echo off
setlocal EnableExtensions
if exist "%~dp0env.cmd" call "%~dp0env.cmd"
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

if not exist "%LOGDIR%" mkdir "%LOGDIR%"
if not exist "%ROOT%\.run" mkdir "%ROOT%\.run"
if not defined CAS_SERVICE_DIR set "CAS_SERVICE_DIR=%ROOT%\etc\cas\services"
if not defined CAS_ADMIN_TOKEN echo [cas] warning: CAS_ADMIN_TOKEN is empty; control plane will return 503.

echo [cas] starting...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$p = Start-Process -FilePath '%JAVA_HOME%\bin\java.exe' -ArgumentList '-jar','%ROOT%\target\cas.war' -WorkingDirectory '%ROOT%' -RedirectStandardOutput '%LOGDIR%\cas.out.log' -RedirectStandardError '%LOGDIR%\cas.err.log' -PassThru -WindowStyle Hidden; Set-Content -Path '%PIDFILE%' -Value $p.Id"
if errorlevel 1 goto start_failed

set /a WAIT=0
:wait
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080/cas/login' -TimeoutSec 2; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
if not errorlevel 1 goto ready
set /a WAIT+=1
if %WAIT% GEQ 60 goto timeout
"%SystemRoot%\System32\ping.exe" -n 2 127.0.0.1 >nul
goto wait

:ready
echo [cas] ready: http://localhost:8080/cas
echo [cas] logs: logs\cas.out.log / logs\cas.err.log
endlocal & exit /b 0

:timeout
echo [cas] start timeout. See logs\cas.out.log and logs\cas.err.log
call "%~dp0stop-cas.cmd" >nul 2>&1
endlocal & exit /b 1

:no_java
echo [cas] JAVA_HOME is not set or invalid. Edit bin\env.cmd first.
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
echo [cas] port 8080 is already in use. Run bin\stop-cas.cmd first.
endlocal & exit /b 1

:start_failed
echo [cas] failed to start java process.
endlocal & exit /b 1
