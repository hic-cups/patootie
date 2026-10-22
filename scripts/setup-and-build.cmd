@echo off
rem Double-click this, or run scripts\setup-and-build.cmd from Command Prompt.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-and-build.ps1"
set RESULT=%ERRORLEVEL%
echo.
pause
exit /b %RESULT%
