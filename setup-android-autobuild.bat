@echo off
REM Launcher only - the real work is in setup-android-autobuild.ps1, kept separate because a
REM multi-line PowerShell block inside a .bat breaks the batch parser (which is why the previous
REM version closed instantly).
cd /d "%~dp0"
if not exist "setup-android-autobuild.ps1" (
  echo setup-android-autobuild.ps1 is missing - it must sit next to this file.
  pause
  exit /b
)
powershell -NoProfile -ExecutionPolicy Bypass -File "setup-android-autobuild.ps1"
