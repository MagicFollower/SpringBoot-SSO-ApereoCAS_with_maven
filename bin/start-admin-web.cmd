@echo off
setlocal EnableExtensions
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
set "PIDFILE=%ROOT%\.run\admin-web.pid"
set "LOGDIR=%ROOT%\logs"
set "WEBROOT=%ROOT%\sso-admin-web"
set "VITE=%WEBROOT%\node_modules\vite\bin\vite.js"

where node.exe >nul 2>&1
if errorlevel 1 goto no_node
if not exist "%WEBROOT%\dist\index.html" goto no_dist
if not exist "%VITE%" goto no_deps

netstat -ano | findstr /C:":5174" | findstr /C:"LISTENING" >nul
if not errorlevel 1 goto port_used

if not exist "%LOGDIR%" mkdir "%LOGDIR%"
if not exist "%ROOT%\.run" mkdir "%ROOT%\.run"

echo [admin-web] starting preview server...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$p = Start-Process -FilePath 'node.exe' -ArgumentList '%VITE%','preview','--host','localhost','--port','5174' -WorkingDirectory '%WEBROOT%' -RedirectStandardOutput '%LOGDIR%\admin-web.out.log' -RedirectStandardError '%LOGDIR%\admin-web.err.log' -PassThru -WindowStyle Hidden; Set-Content -Path '%PIDFILE%' -Value $p.Id"
if errorlevel 1 goto start_failed

set /a WAIT=0
:wait
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:5174/' -TimeoutSec 2; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
if not errorlevel 1 goto ready
set /a WAIT+=1
if %WAIT% GEQ 60 goto timeout
"%SystemRoot%\System32\ping.exe" -n 2 127.0.0.1 >nul
goto wait

:ready
echo [admin-web] ready: http://localhost:5174
echo [admin-web] logs: logs\admin-web.out.log / logs\admin-web.err.log
endlocal & exit /b 0

:timeout
echo [admin-web] start timeout. See logs\admin-web.out.log and logs\admin-web.err.log
call "%~dp0stop-admin-web.cmd" >nul 2>&1
endlocal & exit /b 1

:no_node
echo [admin-web] node.exe not found in PATH.
endlocal & exit /b 1

:no_dist
echo [admin-web] dist\index.html not found. Run bin\build-admin-web.cmd first.
endlocal & exit /b 1

:no_deps
echo [admin-web] node_modules\vite not found. Run bin\build-admin-web.cmd first.
endlocal & exit /b 1

:port_used
echo [admin-web] port 5174 is already in use. Run bin\stop-admin-web.cmd first.
endlocal & exit /b 1

:start_failed
echo [admin-web] failed to start node process.
endlocal & exit /b 1
