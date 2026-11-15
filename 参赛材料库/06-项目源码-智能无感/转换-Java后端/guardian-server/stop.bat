@echo off
setlocal enabledelayedexpansion

REM ============================================================================
REM  AI Rhythm Guardian - stop everything
REM  Stops: Java backend + Redis  (MySQL service is left running by default)
REM ============================================================================

set "SCRIPT_DIR=%~dp0"

echo.
echo ============================================================
echo            AI Rhythm Guardian - Stopping
echo ============================================================
echo.

REM ---------------- 1. backend ----------------
echo [1/3] Backend (Java)

set FOUND=0
for /f "tokens=2" %%p in ('tasklist /fi "imagename eq java.exe" /fo list 2^>nul ^| findstr /i "PID:"') do (
    taskkill /f /pid %%p >nul 2>&1
    echo       killed Java PID=%%p
    set FOUND=1
)

if "%FOUND%"=="0" (
    echo       no running backend
) else (
    echo       backend stopped
)

REM ---------------- 2. Redis ----------------
echo.
echo [2/3] Redis

choice /c YN /m "      Stop Redis too? (N = keep, recommended)"
if errorlevel 2 goto :keep_redis

taskkill /f /im redis-server.exe >nul 2>&1
if errorlevel 1 (
    echo       Redis not running
) else (
    echo       Redis stopped
)
goto :mysql_step

:keep_redis
echo       Redis kept running

REM ---------------- 3. MySQL ----------------
:mysql_step
echo.
echo [3/3] MySQL

choice /c YN /m "      Stop MySQL service too? (N = keep, recommended)"
if errorlevel 2 goto :keep_mysql

net stop MYSQL80 >nul 2>&1
if errorlevel 1 (
    echo       cannot stop ^(not running, or need admin rights^)
) else (
    echo       MySQL stopped
)
goto :done

:keep_mysql
echo       MySQL kept running

:done
echo.
echo ============================================================
echo                      Stopped
echo ============================================================
echo.
echo   Note: MySQL is a Windows service.
echo         To make it auto start on boot, run once:
echo           enable-mysql-autostart.bat
echo.
if not defined NO_PAUSE pause
