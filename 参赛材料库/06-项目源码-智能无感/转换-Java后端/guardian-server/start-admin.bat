@echo off
setlocal enabledelayedexpansion

REM ============================================================================
REM  AI Rhythm Guardian - launch admin console (backend + web admin)
REM
REM  Opens TWO windows:
REM    1. backend  - Spring Boot, KEEPS VISIBLE because the dev-mode SMS
REM                  verification code is printed there
REM    2. frontend - Vite dev server on port 5177
REM
REM  Then opens the browser automatically.
REM ============================================================================

set "SCRIPT_DIR=%~dp0"
set "ADMIN_DIR=%SCRIPT_DIR%..\后台管理-Coze版"
set "JDK_HOME=C:\Users\msr150174\.jdks\openjdk-25"
set "NODE_HOME=D:\Program Files\nodejs"
set "LOG_DIR=%SCRIPT_DIR%logs"
set "API_BASE=http://localhost:3000"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo.
echo ============================================================
echo        AI Rhythm Guardian - Admin Console
echo ============================================================
echo.

REM ==================== 1. MySQL + Redis + backend ====================
echo [1/3] Backend

sc query "MYSQL80" 2>nul | findstr /i "RUNNING" >nul
if errorlevel 1 (
    echo       starting MySQL...
    net start "MYSQL80" >nul 2>&1
    ping -n 5 127.0.0.1 >nul
) else (
    echo       MySQL running
)

tasklist /fi "imagename eq redis-server.exe" 2>nul | findstr /i "redis-server.exe" >nul
if errorlevel 1 (
    echo       starting Redis...
    if exist "D:\redis\redis-server.exe" (
        start "Redis" /min "D:\redis\redis-server.exe" "D:\redis\redis.windows.conf"
        ping -n 4 127.0.0.1 >nul
    ) else (
        echo       [WARN] redis not found at D:\redis
    )
) else (
    echo       Redis running
)

REM already listening?
powershell -NoProfile -Command "try { $r = Invoke-RestMethod '%API_BASE%/api/health' -TimeoutSec 2; if ($r.data.status) { exit 0 } } catch { exit 1 }" >nul 2>&1
if not errorlevel 1 (
    echo       backend already running
    goto :frontend
)

echo       compiling and starting backend (this may take a minute)...
REM start.bat opens its own visible window; NO_PAUSE so it does not block
set NO_PAUSE=1
start "GuardianBackend-Launcher" cmd /c ""%SCRIPT_DIR%start.bat""

echo       waiting for backend...
set /a W=0
:waitloop
ping -n 4 127.0.0.1 >nul
set /a W+=3
powershell -NoProfile -Command "try { $r = Invoke-RestMethod '%API_BASE%/api/health' -TimeoutSec 3; if ($r.data.status) { exit 0 } } catch { exit 1 }" >nul 2>&1
if not errorlevel 1 goto :backend_ok
if %W% lss 120 goto :waitloop

echo       [WARN] backend not ready after 120s
echo              check the GuardianBackend window for errors
goto :frontend

:backend_ok
echo       backend ready

REM ==================== 2. frontend ====================
:frontend
echo.
echo [2/3] Web admin

if not exist "%ADMIN_DIR%\package.json" (
    echo       [ERROR] admin project not found:
    echo               %ADMIN_DIR%
    goto :done
)

if not exist "%ADMIN_DIR%\node_modules" (
    echo       dependencies missing, installing ^(first run, takes 1-2 min^)...
    start "npm install" cmd /c "cd /d "%ADMIN_DIR%" && "%NODE_HOME%\npm.cmd" install && echo. && echo install finished && pause"
    echo       after install finishes, run this file again
    goto :done
)

powershell -NoProfile -Command "try { $r = Invoke-WebRequest 'http://localhost:5177' -TimeoutSec 2 -UseBasicParsing; exit 0 } catch { exit 1 }" >nul 2>&1
if not errorlevel 1 (
    echo       web admin already running
    goto :open
)

echo       starting Vite dev server...
start "GuardianAdmin" cmd /k "cd /d "%ADMIN_DIR%" && "%NODE_HOME%\npm.cmd" run dev"

echo       waiting for web admin...
set /a W2=0
:waitloop2
ping -n 3 127.0.0.1 >nul
set /a W2+=2
powershell -NoProfile -Command "try { $r = Invoke-WebRequest 'http://localhost:5177' -TimeoutSec 2 -UseBasicParsing; exit 0 } catch { exit 1 }" >nul 2>&1
if not errorlevel 1 goto :open
if %W2% lss 60 goto :waitloop2

echo       [WARN] web admin not ready, check the GuardianAdmin window
goto :done

REM ==================== 3. open browser ====================
:open
echo.
echo [3/3] Opening browser
start "" "http://localhost:5177"

echo.
echo ============================================================
echo                       READY
echo ============================================================
echo.
echo   Web admin : http://localhost:5177
echo   Backend   : %API_BASE%/api
echo.
echo   Login
echo     phone : 13800000001
echo     code  : see the GuardianBackend window
echo             (a boxed message shows the 6 digit code)
echo.
echo   Windows opened
echo     GuardianBackend  - backend log and SMS codes  ^(do not close^)
echo     GuardianAdmin    - Vite dev server            ^(do not close^)
echo.
echo   Stop: run stop.bat, or close those two windows
echo.

:done
if not defined NO_PAUSE pause
exit /b 0
