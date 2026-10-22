@echo off
setlocal EnableExtensions
echo === start CAS ===
call "%~dp0start-cas.cmd"
if errorlevel 1 exit /b 1

echo === start admin-server ===
call "%~dp0start-admin-server.cmd"
if errorlevel 1 exit /b 1

echo === start admin-web ===
call "%~dp0start-admin-web.cmd"
if errorlevel 1 exit /b 1

echo All services started.
echo   CAS:          http://localhost:8080/cas
echo   admin-server: http://127.0.0.1:8084
echo   admin-web:    http://localhost:5174
endlocal & exit /b 0
