@echo off
REM Launcher only - all the work is in publish-app.ps1, because batch quoting
REM around the version-bump and upload was fragile enough to close on sight.
REM Keep both files together in the orglam-app folder.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0publish-app.ps1"
if errorlevel 1 pause
