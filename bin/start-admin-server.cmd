@echo off
setlocal EnableExtensions
if exist "%~dp0env.cmd" call "%~dp0env.cmd"
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
set "PIDFILE=%ROOT%\.run\admin-server.pid"
set "LOGDIR=%ROOT%\logs"
set "JAR=%ROOT%\admin-server\target\admin-server-1.0.0-SNAPSHOT.jar"

if not defined JAVA_HOME goto no_java
if not exist "%JAVA_HOME%\bin\java.exe" goto no_java
"%JAVA_HOME%\bin\java.exe" -XshowSettings:properties -version 2>&1 | findstr /C:"java.version = 21" >nul
if errorlevel 1 goto bad_java
if not exist "%JAR%" goto no_jar
if not defined ADMIN_DB_PASSWORD goto no_db
if not defined ADMIN_UI_TOKEN goto no_ui_token
if not defined ADMIN_CONTROL_TOKEN goto no_control_token

netstat -ano | findstr /C:":8084" | findstr /C:"LISTENING" >nul
if not errorlevel 1 goto port_used

if not exist "%LOGDIR%" mkdir "%LOGDIR%"
if not exist "%ROOT%\.run" mkdir "%ROOT%\.run"
if not defined ADMIN_SERVICE_DIR set "ADMIN_SERVICE_DIR=%ROOT%\etc\cas\services"

echo [admin-server] starting...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$p = Start-Process -FilePath '%JAVA_HOME%\bin\java.exe' -ArgumentList '-jar','%JAR%' -WorkingDirectory '%ROOT%\admin-server' -RedirectStandardOutput '%LOGDIR%\admin-server.out.log' -RedirectStandardError '%LOGDIR%\admin-server.err.log' -PassThru -WindowStyle Hidden; Set-Content -Path '%PIDFILE%' -Value $p.Id"
if errorlevel 1 goto start_failed

set /a WAIT=0
:wait
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $c = New-Object System.Net.Sockets.TcpClient; $c.Connect('127.0.0.1',8084); $c.Close(); exit 0 } catch { exit 1 }" >nul 2>&1
if not errorlevel 1 goto ready
set /a WAIT+=1
if %WAIT% GEQ 60 goto timeout
"%SystemRoot%\System32\ping.exe" -n 2 127.0.0.1 >nul
goto wait

:ready
echo [admin-server] ready: http://127.0.0.1:8084
echo [admin-server] logs: logs\admin-server.out.log / logs\admin-server.err.log
endlocal & exit /b 0

:timeout
echo [admin-server] start timeout. See logs\admin-server.out.log and logs\admin-server.err.log
call "%~dp0stop-admin-server.cmd" >nul 2>&1
endlocal & exit /b 1

:no_java
echo [admin-server] JAVA_HOME is not set or invalid. Edit bin\env.cmd first.
endlocal & exit /b 1

:bad_java
echo [admin-server] JDK 21 is required. Current JAVA_HOME=%JAVA_HOME%
endlocal & exit /b 1

:no_jar
echo [admin-server] jar not found. Run bin\build-admin-server.cmd first.
endlocal & exit /b 1

:no_db
echo [admin-server] ADMIN_DB_PASSWORD is not set. Edit bin\env.cmd first.
endlocal & exit /b 1

:no_ui_token
echo [admin-server] ADMIN_UI_TOKEN is not set. Edit bin\env.cmd first.
endlocal & exit /b 1

:no_control_token
echo [admin-server] ADMIN_CONTROL_TOKEN is not set. Edit bin\env.cmd first.
endlocal & exit /b 1

:port_used
echo [admin-server] port 8084 is already in use. Run bin\stop-admin-server.cmd first.
endlocal & exit /b 1

:start_failed
echo [admin-server] failed to start java process.
endlocal & exit /b 1
