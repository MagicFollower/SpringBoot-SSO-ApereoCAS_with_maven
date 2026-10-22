@echo off
setlocal EnableExtensions
set "RC=0"

echo === stop admin-web ===
call "%~dp0stop-admin-web.cmd"
if errorlevel 1 set "RC=1"

echo === stop admin-server ===
call "%~dp0stop-admin-server.cmd"
if errorlevel 1 set "RC=1"

echo === stop CAS ===
call "%~dp0stop-cas.cmd"
if errorlevel 1 set "RC=1"

if "%RC%"=="0" echo All services stopped.
endlocal & exit /b %RC%
