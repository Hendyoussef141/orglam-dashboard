@echo off
REM Launcher only - the work is in set-missing-secrets.ps1 (kept separate so the batch parser
REM cannot break on a multi-line PowerShell block).
cd /d "%~dp0"
if not exist "set-missing-secrets.ps1" (
  echo set-missing-secrets.ps1 is missing - it must sit next to this file.
  pause
  exit /b
)
powershell -NoProfile -ExecutionPolicy Bypass -File "set-missing-secrets.ps1"
