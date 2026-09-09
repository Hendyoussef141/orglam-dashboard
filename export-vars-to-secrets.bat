@echo off
REM Launcher only - the work is in export-vars-to-secrets.ps1 (kept separate so the batch parser
REM cannot break on a multi-line PowerShell block).
cd /d "%~dp0"
if not exist "export-vars-to-secrets.ps1" (
  echo export-vars-to-secrets.ps1 is missing - it must sit next to this file.
  pause
  exit /b
)
powershell -NoProfile -ExecutionPolicy Bypass -File "export-vars-to-secrets.ps1"
