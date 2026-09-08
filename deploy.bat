@echo off
REM ===================================================================
REM  Deploys everything. Run this after you paste updated files into
REM  this folder - it sends them to GitHub, which deploys the worker
REM  and the dashboard automatically.
REM
REM  Lives in the folder that contains "orglam-app".
REM ===================================================================

cd /d "%~dp0"

if not exist ".git" (
  echo No repository here. Run setup-github.bat first.
  goto end
)

echo === What changed ===
git status --short
echo.

git add -A
git diff --cached --quiet
if not errorlevel 1 (
  echo Nothing has changed since the last deploy.
  goto end
)

set MSG=%*
if "%MSG%"=="" set MSG=Update

git commit -m "%MSG%"
git rev-parse --verify HEAD >nul 2>&1
if errorlevel 1 (
  echo The commit failed - see above. Nothing was sent.
  goto end
)

echo.
echo === Sending to GitHub ===
git push origin main
if errorlevel 1 (
  echo.
  echo  The push failed - the reason is above.
  goto end
)

echo.
echo ==========================================
echo  Sent. The deploy is running now.
echo  Watch it here:
echo  https://github.com/Hendyoussef141/orglam-dashboard/actions
echo ==========================================

:end
echo.
pause
