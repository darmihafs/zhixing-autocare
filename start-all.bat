@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
title HongXing Car Wash - Start Services

rem ============================================================
rem  Config: optional JAVA17 override (falls back to JAVA_HOME / PATH)
rem ============================================================
set "JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if defined JAVA17 if exist "%JAVA17%\bin\java.exe" set "JAVA=%JAVA17%\bin\java.exe"

set "SVCS=user-service product-service shop-service order-service pay-service review-service ai-service gateway"
set "PORT_user-service=8081"
set "PORT_product-service=8082"
set "PORT_shop-service=8083"
set "PORT_order-service=8084"
set "PORT_pay-service=8085"
set "PORT_review-service=8086"
set "PORT_ai-service=8087"
set "PORT_gateway=8080"

rem ============================================================
rem  Local secrets: .env.local (git-ignored, injected at runtime only)
rem ============================================================
if exist ".env.local" (
  for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env.local") do (
    if not "%%a"=="" if not "%%b"=="" set "%%a=%%b"
  )
  echo       Loaded .env.local into runtime environment.
)

if not exist logs mkdir logs

echo [1/4] Checking Docker middleware ...
docker info >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Docker Desktop is not running. Start it first, then retry.
  exit /b 1
)
set "MISSING="
for %%C in (hc-mysql hc-redis hc-nacos hc-rocketmq-namesrv hc-rocketmq-broker hc-elasticsearch) do (
  docker ps --format "{{.Names}}" | findstr "%%C" >nul
  if errorlevel 1 set "MISSING=1"
)
if defined MISSING (
  echo       Middleware not all online, running docker compose up -d ...
  docker compose up -d
  if errorlevel 1 (
    echo [ERROR] docker compose up failed.
    exit /b 1
  )
)
echo       Waiting for MySQL/Redis/Nacos/ES to be healthy ...
set /a HTRIES=0
:health
set /a HTRIES+=1
set /a HEALTHY=0
for %%C in (hc-mysql hc-redis hc-nacos hc-elasticsearch) do (
  for /f "delims=" %%H in ('docker inspect --format "{{.State.Health.Status}}" %%C 2^>nul') do (
    if "%%H"=="healthy" set /a HEALTHY+=1
  )
)
if !HEALTHY! equ 4 goto healthy
if !HTRIES! geq 40 (
  echo [ERROR] Middleware not healthy within 120s. Check "docker compose ps".
  exit /b 1
)
%SystemRoot%\System32\ping.exe -n 4 127.0.0.1 >nul
goto health
:healthy
echo       Middleware ready.

echo [2/4] Starting 8 microservices (JDK: !JAVA!) ...
for %%S in (%SVCS%) do (
  call set "PORT=%%PORT_%%S%%"
  netstat -ano | findstr "LISTENING" | findstr ":!PORT! " >nul
  if !errorlevel! equ 0 (
    echo [SKIP] %%S port !PORT! already in use
  ) else (
    if not exist "%%S\target\%%S-1.0.0.jar" (
      echo [ERROR] Missing %%S\target\%%S-1.0.0.jar. Run mvn clean package -DskipTests first.
      exit /b 1
    )
    powershell -NoProfile -Command "Start-Process -FilePath '!JAVA!' -ArgumentList '-Xms128m','-Xmx256m','-Duser.timezone=Asia/Shanghai','-Dfile.encoding=UTF-8','-jar','%%S/target/%%S-1.0.0.jar' -WorkingDirectory '%CD%' -WindowStyle Hidden -RedirectStandardOutput 'logs\%%S.log' -RedirectStandardError 'logs\%%S.err.log'"
    echo [START] %%S -^> logs\%%S.log
  )
)

echo [3/4] Waiting for ports to be ready ...
set /a TRIES=0
:wait
set /a TRIES+=1
set /a UP=0
for %%S in (%SVCS%) do (
  call set "PORT=%%PORT_%%S%%"
  netstat -ano | findstr "LISTENING" | findstr ":!PORT! " >nul
  if !errorlevel! equ 0 set /a UP+=1
)
echo       Ready !UP!/8 (round !TRIES!)
if !UP! equ 8 goto ready
if !TRIES! geq 40 (
  echo [WARN] Not all ready within 120s. Check logs\ for details.
  goto status
)
%SystemRoot%\System32\ping.exe -n 4 127.0.0.1 >nul
goto wait

:ready
echo       All services ready.
:status
echo [4/4] Service status:
for %%S in (%SVCS%) do (
  call set "PORT=%%PORT_%%S%%"
  netstat -ano | findstr "LISTENING" | findstr ":!PORT! " >nul
  if !errorlevel! equ 0 (
    echo   [OK]   %%S  :!PORT!
  ) else (
    echo   [DOWN] %%S  :!PORT!
  )
)

netstat -ano | findstr "LISTENING" | findstr ":5173 " >nul
if !errorlevel! equ 0 (
  echo Frontend Vite already running: http://127.0.0.1:5173
  goto end
)
choice /c YN /m "Also start frontend Vite (http://127.0.0.1:5173)?"
if errorlevel 2 goto end
if not exist frontend\node_modules (
  echo       First run, executing npm install ...
  pushd frontend
  call npm install
  popd
)
powershell -NoProfile -Command "Start-Process -FilePath 'npm.cmd' -ArgumentList 'run','dev' -WorkingDirectory '%CD%\frontend' -WindowStyle Hidden -RedirectStandardOutput '%CD%\logs\frontend.log' -RedirectStandardError '%CD%\logs\frontend.err.log'"
set /a FTRIES=0
:fwait
set /a FTRIES+=1
netstat -ano | findstr "LISTENING" | findstr ":5173 " >nul
if !errorlevel! equ 0 (
  echo Frontend ready: http://127.0.0.1:5173
  goto end
)
if !FTRIES! geq 20 (
  echo [WARN] Vite not ready within 60s. Check logs\frontend.log.
  goto end
)
%SystemRoot%\System32\ping.exe -n 4 127.0.0.1 >nul
goto fwait

:end
echo Done. Gateway http://127.0.0.1:8080 , Frontend http://127.0.0.1:5173
endlocal