@echo off
setlocal EnableExtensions
set "HOST_JAVA_HOME=%JAVA_HOME%"
if exist "%~dp0env.cmd" call "%~dp0env.cmd"
if not defined JAVA_HOME if defined HOST_JAVA_HOME set "JAVA_HOME=%HOST_JAVA_HOME%"
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
pushd "%ROOT%" >nul

if not defined JAVA_HOME goto no_java
if not exist "%JAVA_HOME%\bin\java.exe" goto no_java
"%JAVA_HOME%\bin\java.exe" -XshowSettings:properties -version 2>&1 | findstr /C:"java.version = 21" >nul
if errorlevel 1 goto bad_java

echo [cas] stopping running instance if any...
call "%~dp0stop-cas.cmd" >nul 2>&1

echo [cas] building executable war with JDK 21...
call "%ROOT%\mvnw.cmd" clean package -DskipTests
if errorlevel 1 goto failed

echo [cas] build ok: target\cas.war
popd >nul
endlocal & exit /b 0

:no_java
echo [cas] JAVA_HOME is not set or invalid. Set JDK 21 in the current environment or in bin\env.cmd.
goto failed

:bad_java
echo [cas] JDK 21 is required. Current JAVA_HOME=%JAVA_HOME%
goto failed

:failed
popd >nul
endlocal & exit /b 1
