@echo off
setlocal

REM ============================================================================
REM  Set shared environment variables for BOTH projects
REM     - AI Rhythm Guardian  (guardian-server)
REM     - Campus Errand        (zhipao-server)
REM
REM  Written to the USER level environment, so both projects and any new
REM  terminal pick them up. Run once. No admin rights needed.
REM
REM  NOTE: fill in the two TODO values below before running.
REM ============================================================================

REM ======================= TODO: fill these two =======================
REM
REM  1) NEW_COZE_TOKEN
REM     The old token is dead (Coze returns 4101). Get a new one at
REM     https://www.coze.cn  ->  Workspace  ->  Personal Access Token
REM     Copy the whole string starting with pat_
set "NEW_COZE_TOKEN=PUT_YOUR_NEW_TOKEN_HERE"
REM
REM  2) NEW_COZE_BOT_ID
REM     The two projects used different ids. Check the real one in Coze.
REM       zhipao-server      used 7637945513727672363
REM       guardian-server    used 7644841783033528370
set "NEW_COZE_BOT_ID=7644841783033528370"
REM ====================================================================

echo.
echo ============================================================
echo     Shared keys for BOTH projects
echo     (AI Rhythm Guardian + Campus Errand)
echo ============================================================
echo.

echo %NEW_COZE_TOKEN% | findstr /c:"PUT_YOUR_NEW_TOKEN_HERE" >nul
if not errorlevel 1 (
    echo   [ERROR] COZE_API_TOKEN not filled in yet.
    echo           Open this file in Notepad and edit the TODO section.
    echo.
    pause
    exit /b 1
)
echo %NEW_COZE_BOT_ID% | findstr /c:"PUT_THE_VERIFIED_BOT_ID_HERE" >nul
if not errorlevel 1 (
    echo   [ERROR] COZE_BOT_ID not filled in yet.
    echo           Open this file in Notepad and edit the TODO section.
    echo.
    pause
    exit /b 1
)

echo   Will set these user-level environment variables:
echo     COZE_API_TOKEN, COZE_BOT_ID, COZE_BASE_URL, COZE_ENABLED
echo     WX_APP_ID, WX_APP_SECRET, JWT_SECRET, TENCENT_MAP_KEY
echo     MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD
echo     REDIS_HOST, REDIS_PORT, REDIS_DB
echo     AI_ENABLED, OLLAMA_ENABLED, OLLAMA_BASE_URL, OLLAMA_MODEL
echo     SMS_DEV_MODE
echo.
echo   Existing values will be OVERWRITTEN.
echo.

choice /c YN /m "Continue"
if errorlevel 2 goto :cancel

echo.
echo   [1/2] Setting...

setx COZE_API_TOKEN "%NEW_COZE_TOKEN%" >nul
setx COZE_BOT_ID "%NEW_COZE_BOT_ID%" >nul
setx COZE_BASE_URL "https://api.coze.cn" >nul
setx COZE_ENABLED "true" >nul

setx WX_APP_ID "wxc9c6da82f1b21327" >nul
setx WX_APP_SECRET "" >nul

setx JWT_SECRET "" >nul
setx TENCENT_MAP_KEY "" >nul

setx MYSQL_HOST "localhost" >nul
setx MYSQL_PORT "3306" >nul
setx MYSQL_USER "root" >nul
setx MYSQL_PASSWORD "" >nul

setx REDIS_HOST "localhost" >nul
setx REDIS_PORT "6379" >nul
setx REDIS_DB "0" >nul

setx AI_ENABLED "true" >nul
setx OLLAMA_ENABLED "false" >nul
setx OLLAMA_BASE_URL "http://localhost:11434" >nul
setx OLLAMA_MODEL "qwen2.5:7b" >nul

setx SMS_DEV_MODE "true" >nul

echo         done
echo.
echo   [2/2] Note
echo.
echo         MYSQL_DB is intentionally NOT set:
echo           guardian-server  uses  znwg
echo           zhipao-server   uses  xyzp
echo         Each project keeps its own default.
echo.
echo   Variables are visible to any NEW terminal window.
echo   Close and reopen your terminal (and IDEA) to pick them up.
echo.
pause
exit /b 0

:cancel
echo.
echo   Cancelled, nothing changed.
echo.
pause
