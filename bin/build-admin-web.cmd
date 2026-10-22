@echo off
setlocal EnableExtensions
for %%I in ("%~dp0..") do set "ROOT=%%~fI"

where node.exe >nul 2>&1
if errorlevel 1 goto no_node
where npm.cmd >nul 2>&1
if errorlevel 1 goto no_node

echo [admin-web] stopping running preview if any...
call "%~dp0stop-admin-web.cmd" >nul 2>&1

pushd "%ROOT%\sso-admin-web" >nul
if not exist "node_modules\vite" (
  echo [admin-web] installing dependencies with npm ci...
  call npm.cmd ci
  if errorlevel 1 goto failed
)

echo [admin-web] building static assets...
call npm.cmd run build
if errorlevel 1 goto failed

popd >nul
echo [admin-web] build ok: sso-admin-web\dist
endlocal & exit /b 0

:no_node
echo [admin-web] node.exe or npm.cmd not found in PATH.
endlocal & exit /b 1

:failed
popd >nul
endlocal & exit /b 1
