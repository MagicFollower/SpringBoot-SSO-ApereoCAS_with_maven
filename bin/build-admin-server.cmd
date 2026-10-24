@echo off
setlocal EnableExtensions
set "HOST_JAVA_HOME=%JAVA_HOME%"
if exist "%~dp0env.cmd" call "%~dp0env.cmd"
if not defined JAVA_HOME if defined HOST_JAVA_HOME set "JAVA_HOME=%HOST_JAVA_HOME%"
for %%I in ("%~dp0..") do set "ROOT=%%~fI"

if not defined JAVA_HOME goto no_java
if not exist "%JAVA_HOME%\bin\java.exe" goto no_java
"%JAVA_HOME%\bin\java.exe" -XshowSettings:properties -version 2>&1 | findstr /C:"java.version = 21" >nul
if errorlevel 1 goto bad_java

echo [admin-server] stopping running instance if any...
call "%~dp0stop-admin-server.cmd" >nul 2>&1

echo [admin-server] building fat jar with JDK 21...
pushd "%ROOT%\admin-server" >nul
call "%ROOT%\admin-server\mvnw.cmd" clean package -DskipTests
set "RC=%ERRORLEVEL%"
popd >nul
if not "%RC%"=="0" goto failed

echo [admin-server] build ok: admin-server\target\admin-server-1.0.0-SNAPSHOT.jar
endlocal & exit /b 0

:no_java
echo [admin-server] JAVA_HOME is not set or invalid. Set JDK 21 in the current environment or in bin\env.cmd.
goto failed

:bad_java
echo [admin-server] JDK 21 is required. Current JAVA_HOME=%JAVA_HOME%
goto failed

:failed
endlocal & exit /b 1
