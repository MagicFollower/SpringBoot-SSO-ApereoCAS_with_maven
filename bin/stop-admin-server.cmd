@echo off
setlocal EnableExtensions
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
set "PIDFILE=%ROOT%\.run\admin-server.pid"
set "MARKER=admin-server-1.0.0-SNAPSHOT.jar"

if not exist "%PIDFILE%" (
  echo [admin-server] not running: no pid file.
  endlocal & exit /b 0
)

set "PID="
set /p PID=<"%PIDFILE%"
if not defined PID (
  del /q "%PIDFILE%" >nul 2>&1
  echo [admin-server] stale pid file removed.
  endlocal & exit /b 0
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "$p = Get-CimInstance Win32_Process -Filter 'ProcessId=%PID%' -ErrorAction SilentlyContinue; if ($p -and $p.CommandLine -like '*%MARKER%*') { Stop-Process -Id %PID% -Force; exit 0 } else { exit 1 }"
if errorlevel 1 (
  echo [admin-server] pid %PID% is not the expected admin-server process; not killed.
  del /q "%PIDFILE%" >nul 2>&1
  endlocal & exit /b 1
)

del /q "%PIDFILE%" >nul 2>&1
echo [admin-server] stopped pid %PID%.
endlocal & exit /b 0
