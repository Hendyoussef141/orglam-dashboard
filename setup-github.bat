@echo off
REM ===================================================================
REM  Just retries the push and keeps the window open so the real error
REM  is readable. Put next to setup-github.bat, in the folder that
REM  contains "orglam-app", and double-click it.
REM ===================================================================

cd /d "%~dp0"

if not exist ".git" (
  echo No repository here. Run setup-github.bat first.
  goto end
)

echo === What is waiting to be sent ===
git log --oneline -1
git remote -v
echo.

echo === Pushing to GitHub ===
echo (A browser or a login box may appear - approve it.)
echo.
git push -u origin main
echo.
if errorlevel 1 (
  echo -----------------------------------------------
  echo  The push did NOT succeed. The reason is above.
  echo  Copy that text and send it to me.
  echo -----------------------------------------------
) else (
  echo -----------------------------------------------
  echo  Sent. Tell me and I will finish the auto-deploy.
  echo -----------------------------------------------
)

:end
echo.
pause
