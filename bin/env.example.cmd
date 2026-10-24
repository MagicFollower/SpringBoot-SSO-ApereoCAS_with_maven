@echo off
rem Copy this file to env.cmd and fill in real values.
rem env.cmd is ignored by git. Never commit real passwords or tokens.
rem
rem JAVA_HOME is optional here. If it is not set, the scripts use the
rem current Windows JAVA_HOME. Uncomment and set it only when the default
rem JDK is not 21:
rem set "JAVA_HOME=C:\Program Files\Java\jdk-21"
rem
rem CAS database URL override (optional). Default: localhost cas_sso.
rem Keep all query parameters, or MySQL may fail on timezone/public key.
rem set "CAS_DB_URL=jdbc:mysql://localhost:3306/cas_sso?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8"
rem set "CAS_DB_USER=root"
rem
rem Admin database URL override (optional). It may point to another schema
rem or host than CAS. Default: localhost cas_sso.
rem set "ADMIN_DB_URL=jdbc:mysql://localhost:3306/cas_sso?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8"
rem set "ADMIN_DB_USER=root"
rem
rem Database passwords (required; use different values when the URLs point
rem to different databases/accounts).
set "CAS_DB_PASSWORD=change-me"
set "ADMIN_DB_PASSWORD=change-me"
rem
rem TOKEN ROLES:
rem CAS_ADMIN_TOKEN     CAS control-plane token. Admin-server sends it for
rem                     sessions, kickout and service deletion. Empty = 503.
rem ADMIN_UI_TOKEN      Token used by sso-admin-web to call admin-server.
rem                     Empty disables the admin API (503).
rem ADMIN_CONTROL_TOKEN Token admin-server uses to call the CAS control plane.
rem                     Must be identical to CAS_ADMIN_TOKEN.
set "CAS_ADMIN_TOKEN=change-me"
set "ADMIN_UI_TOKEN=change-me"
set "ADMIN_CONTROL_TOKEN=change-me"
