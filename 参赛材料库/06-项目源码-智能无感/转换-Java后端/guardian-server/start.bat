@echo off
setlocal enabledelayedexpansion

REM ============================================================================
REM  AI Rhythm Guardian - one click start
REM  MySQL + Redis + DB schema -> compile -> run backend -> wait ready
REM  Double click this file. Use stop.bat to stop.
REM ============================================================================

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

REM ---------------- paths (edit if needed) ----------------
set "JDK_HOME=C:\Users\msr150174\.jdks\openjdk-25"
set "M2_REPO=C:\Users\msr150174\.m2\repository"
set "REDIS_DIR=D:\redis"
set "MYSQL_HOME=D:\mysql"
set "MAIN_CLASS=com.guardian.GuardianApplication"
set "API_BASE=http://localhost:3000"
set "OUT_DIR=%SCRIPT_DIR%target\classes"
set "RES_DIR=%SCRIPT_DIR%src\main\resources"
set "LOG_DIR=%SCRIPT_DIR%logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo.
echo ============================================================
echo          AI Rhythm Guardian - Starting
echo ============================================================
echo.

REM ==================== 1-3. infrastructure ====================
call "%SCRIPT_DIR%infra-check.bat"
if errorlevel 1 (
    echo.
    echo [ERROR] infrastructure check failed
    goto :fail
)

REM ==================== 4. compile ====================
echo [4/5] Compile

if not exist "%JDK_HOME%\bin\javac.exe" (
    echo       [ERROR] JDK not found: %JDK_HOME%
    goto :fail
)

set "LOMBOK_JAR="
if exist "%M2_REPO%\org\projectlombok\lombok\1.18.48\lombok-1.18.48.jar" (
    set "LOMBOK_JAR=%M2_REPO%\org\projectlombok\lombok\1.18.48\lombok-1.18.48.jar"
)
if "%LOMBOK_JAR%"=="" (
    for /d %%d in ("%M2_REPO%\org\projectlombok\lombok\*") do (
        for %%f in ("%%d\lombok-*.jar") do set "LOMBOK_JAR=%%f"
    )
)
if "%LOMBOK_JAR%"=="" (
    echo       [ERROR] lombok jar not found in %M2_REPO%
    goto :fail
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%build-helper.ps1" ^
  -M2Repo "%M2_REPO%" ^
  -SrcDir "%SCRIPT_DIR%src\main\java" ^
  -OutDir "%OUT_DIR%" ^
  -ResDir "%RES_DIR%" ^
  -LombokJar "%LOMBOK_JAR%" ^
  -TempDir "%TEMP%"

if errorlevel 1 (
    echo       [ERROR] classpath build failed
    goto :fail
)

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

echo       compiling...
"%JDK_HOME%\bin\javac.exe" "@%TEMP%\guardian_javac.txt"
if errorlevel 1 (
    echo.
    echo       [ERROR] compile failed, see errors above
    echo       note: javac outputs Chinese, judge by exit code not by grep
    goto :fail
)
echo       compile ok

REM ==================== 5. run ====================
echo.
echo [5/5] Start backend

REM 端口已改回 3000（默认值），见 src\main\resources\application.yml。
REM 3000 若被其他程序占用（例如 new-api），需要先停掉它，否则本后端起不来。
REM 临时换端口：set SERVER_PORT=8080 后再运行本脚本。

REM stop any existing instance first, avoid port conflict
for /f "tokens=2" %%p in ('tasklist /fi "imagename eq java.exe" /fo list 2^>nul ^| findstr /i "PID:"') do (
    taskkill /f /pid %%p >nul 2>&1
)
ping -n 3 127.0.0.1 >nul

REM The runtime classpath is ~14 KB. Reading it with `set /p` truncates around
REM 8 KB and mangles special characters, which shows up as
REM ClassNotFoundException. build-helper.ps1 already wrote a JVM argfile,
REM so just point java at it.

if not exist "%TEMP%\guardian_run.txt" (
    echo       [ERROR] run argfile missing, build step failed
    goto :fail
)

REM ★ 后端单独开一个【可见】窗口，不要 /min、不要重定向到文件。
REM   原因：开发模式下短信不真发，验证码是打印在控制台的，
REM   如果输出到文件或窗口最小化，用户就看不到验证码，没法登录。
REM   日志同时仍然写入 logs\backend.log（java 内部由 logback 负责）。
start "GuardianBackend" cmd /k ""%JDK_HOME%\bin\java.exe" -Dfile.encoding=UTF-8 @%TEMP%\guardian_run.txt"

echo       waiting for ready...
set /a WAIT=0
:waitloop
ping -n 4 127.0.0.1 >nul
set /a WAIT+=3
powershell -NoProfile -Command "try { $r = Invoke-RestMethod '%API_BASE%/api/health' -TimeoutSec 3; if ($r.data.status) { exit 0 } } catch { exit 1 }" >nul 2>&1
if not errorlevel 1 goto :ready
if %WAIT% lss 60 goto :waitloop

echo       [WARN] not ready in 60s, check %LOG_DIR%\backend.log
goto :fail

:ready
echo.
echo ============================================================
echo                    STARTED OK
echo ============================================================
echo.
powershell -NoProfile -Command "try { $h = Invoke-RestMethod '%API_BASE%/api/health' -TimeoutSec 5; Write-Host ('   status : ' + $h.data.status); Write-Host ('   mysql  : ' + $h.data.mysql.connected + '  (' + $h.data.mysql.tables + ' tables)'); Write-Host ('   redis  : ' + $h.data.redis.connected); Write-Host ('   ai     : ' + $h.data.ai.provider + '  (fallback: ' + $h.data.ai.fallback + ')') } catch { Write-Host '   health check failed' }"
echo.
echo   api    : %API_BASE%/api
echo   health : %API_BASE%/api/health
echo   log    : %LOG_DIR%\backend.log
echo   stop   : run stop.bat
echo.
if not defined NO_PAUSE pause
exit /b 0

:fail
echo.
echo   infrastructure : MySQL / Redis / schema
echo   log            : %LOG_DIR%\backend.log
echo.
if not defined NO_PAUSE pause
exit /b 1
