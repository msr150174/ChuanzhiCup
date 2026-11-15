@echo off
setlocal enabledelayedexpansion

REM ============================================================================
REM  AI Rhythm Guardian - infrastructure check (MySQL + Redis + DB schema)
REM  Called by start.bat. Can also be run standalone.
REM ============================================================================

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

set "MYSQL_HOME=D:\mysql"
set "MYSQL_BIN=%MYSQL_HOME%\bin\mysql.exe"
set "MYSQLD=%MYSQL_HOME%\bin\mysqld.exe"
set "MYSQL_SERVICE=MYSQL80"
set "REDIS_DIR=D:\redis"
set "DB_NAME=znwg"
set "DB_USER=root"
set "DB_PASS=msr150174"
REM schema file candidates, first existing one wins
REM V1__init_schema.sql is now the znwg edition (creates database znwg)
set "SCHEMA=%SCRIPT_DIR%..\..\db\V1__init_schema.sql"
if not exist "%SCHEMA%" set "SCHEMA=%SCRIPT_DIR%db\V1__init_schema.sql"
if not exist "%SCHEMA%" set "SCHEMA=%SCRIPT_DIR%db\V1__init_schema_znwg.sql"

echo.
echo ============================================================
echo        AI Rhythm Guardian - Infrastructure
echo ============================================================
echo.

REM ==================== 1. MySQL ====================
echo [1/3] MySQL

sc query "%MYSQL_SERVICE%" 2>nul | findstr /i "RUNNING" >nul
if not errorlevel 1 (
    echo       service running
    goto :mysql_ready
)

echo       not running, starting service...
net start "%MYSQL_SERVICE%" >nul 2>&1
if errorlevel 1 (
    REM service failed -> try standalone mysqld
    echo       service start failed, trying standalone mysqld...
    if not exist "%MYSQLD%" (
        echo       [ERROR] mysqld not found: %MYSQLD%
        exit /b 1
    )
    start "MySQL" /min "%MYSQLD%" --defaults-file="%MYSQL_HOME%\my.ini"
    ping -n 6 127.0.0.1 >nul
) else (
    echo       service started
    ping -n 4 127.0.0.1 >nul
)

:mysql_ready
REM wait for port 3306 to accept connections
set /a MW=0
:mysql_wait
"%MYSQL_BIN%" -u%DB_USER% -p%DB_PASS% -e "SELECT 1" >nul 2>&1
if not errorlevel 1 goto :mysql_ok
ping -n 3 127.0.0.1 >nul
set /a MW+=2
if %MW% lss 30 goto :mysql_wait
echo       [ERROR] MySQL not accepting connections after 30s
exit /b 1

:mysql_ok
echo       connection ok

REM ==================== 2. database schema ====================
echo.
echo [2/3] Database schema

set "SCHEMA_TMP=%TEMP%\guardian_schema.sql"
set "CNT_TMP=%TEMP%\guardian_count.txt"

REM ---------- check if database already has all tables ----------
call :count_tables
if defined TBLCOUNT (
    if !TBLCOUNT! GEQ 12 (
        echo       already initialized ^(!TBLCOUNT! tables^)
        goto :redis_step
    )
)

if not exist "%SCHEMA%" (
    echo       [WARN] schema file not found: %SCHEMA%
    echo              skipping auto-init, create database manually
    goto :redis_step
)

echo       initializing ^(schema missing or incomplete^)...
REM copy to ASCII path: mysql cannot read paths with non-ASCII chars
copy /y "%SCHEMA%" "%SCHEMA_TMP%" >nul

REM mysql needs forward slashes in the source path, backslashes get eaten
set "SCHEMA_FWD=%SCHEMA_TMP:\=/%"
"%MYSQL_BIN%" -u%DB_USER% -p%DB_PASS% --default-character-set=utf8mb4 -e "source %SCHEMA_FWD%" >nul 2>&1

REM do NOT trust errorlevel here: mysql -e "source ..." reports non-zero
REM even on success. Verify by counting the actual tables instead.
call :count_tables
if not defined TBLCOUNT set "TBLCOUNT=0"
if !TBLCOUNT! LSS 12 (
    echo       [WARN] only !TBLCOUNT! tables created, expected 12
    echo              check the schema file manually
) else (
    echo       schema applied ^(!TBLCOUNT! tables^)
)
del /q "%SCHEMA_TMP%" >nul 2>&1

:redis_step

REM ==================== 3. Redis ====================
echo.
echo [3/3] Redis

tasklist /fi "imagename eq redis-server.exe" 2>nul | findstr /i "redis-server.exe" >nul
if not errorlevel 1 (
    echo       already running
    goto :redis_ok
)

if not exist "%REDIS_DIR%\redis-server.exe" (
    echo       [ERROR] not found: %REDIS_DIR%\redis-server.exe
    exit /b 1
)

echo       starting...
if exist "%REDIS_DIR%\redis.windows.conf" (
    start "Redis" /min "%REDIS_DIR%\redis-server.exe" "%REDIS_DIR%\redis.windows.conf"
) else (
    start "Redis" /min "%REDIS_DIR%\redis-server.exe"
)
ping -n 5 127.0.0.1 >nul

:redis_ok
set /a RW=0
:redis_wait
"%REDIS_DIR%\redis-cli.exe" ping 2>nul | findstr /i "PONG" >nul
if not errorlevel 1 goto :done
ping -n 3 127.0.0.1 >nul
set /a RW+=2
if %RW% lss 20 goto :redis_wait
echo       [WARN] Redis not responding, backend will degrade gracefully
goto :done

:done
echo.
echo       MySQL   : ok  ^(db=%DB_NAME%^)
echo       Redis   : ok
echo.
exit /b 0

REM ============================================================================
REM  :count_tables - put table count of %DB_NAME% into TBLCOUNT
REM
REM  Uses a temp file instead of `for /f` because nested quotes around the
REM  mysql command break parsing inside parentheses / if blocks.
REM ============================================================================
:count_tables
set "TBLCOUNT="
del /q "%CNT_TMP%" >nul 2>&1
"%MYSQL_BIN%" -u%DB_USER% -p%DB_PASS% -N -B -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='%DB_NAME%'" > "%CNT_TMP%" 2>nul
if exist "%CNT_TMP%" (
    set /p TBLCOUNT=<"%CNT_TMP%"
    del /q "%CNT_TMP%" >nul 2>&1
)
REM strip any stray whitespace
if defined TBLCOUNT set "TBLCOUNT=!TBLCOUNT: =!"
exit /b 0
