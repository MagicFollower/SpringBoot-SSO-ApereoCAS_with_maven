@echo off
setlocal EnableExtensions
echo === build CAS ===
call "%~dp0build-cas.cmd"
if errorlevel 1 exit /b 1

echo === build admin-server ===
call "%~dp0build-admin-server.cmd"
if errorlevel 1 exit /b 1

echo === build admin-web ===
call "%~dp0build-admin-web.cmd"
if errorlevel 1 exit /b 1

echo All builds completed.
endlocal & exit /b 0
