@echo off
REM ============================================================================
REM  Make MySQL service start automatically on boot
REM  Requires administrator rights - this script self-elevates.
REM
REM  Just double click it once. After that MySQL starts with Windows.
REM ============================================================================

net session >nul 2>&1
if not errorlevel 1 goto :run

REM ---- not admin: re-launch elevated ----
echo.
echo   This script needs administrator rights to change the MySQL service.
echo   A UAC prompt will appear - please click "Yes".
echo.
powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
exit /b

:run
echo.
echo ============================================================
echo        Enable MySQL auto start on boot
echo ============================================================
echo.

sc config MYSQL80 start= auto
if errorlevel 1 (
    echo.
    echo   [ERROR] failed to change service startup type
    echo           check that the service name is MYSQL80:
    echo           sc query type= service state= all ^| findstr /i mysql
    echo.
    pause
    exit /b 1
)

sc start MYSQL80 >nul 2>&1

echo   current status:
sc qc MYSQL80 | findstr /i "START_TYPE"
sc query MYSQL80 | findstr /i "STATE"

echo.
echo   Done. MySQL will now start automatically with Windows.
echo.
pause
