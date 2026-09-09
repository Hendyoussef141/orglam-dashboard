@echo off
REM ===================================================================
REM  Your local folder is the state you want. The remote has an older
REM  commit that the earlier reset dropped, which is why a normal push
REM  is refused. This replaces the remote branch with your local one.
REM
REM  --force-with-lease, not --force: it refuses if someone else pushed
REM  in the meantime, so nothing can be silently overwritten.
REM ===================================================================

cd /d "%~dp0"

if not exist ".git" (
  echo This is not the project folder - put this file next to deploy.bat.
  goto end
)

echo === Local commits not on GitHub ===
git log --oneline origin/main..HEAD
echo.

echo === Replacing the remote branch with your local one ===
git push --force-with-lease origin main
if errorlevel 1 (
  echo.
  echo  Still refused - paste the message above to me.
  goto end
)

echo.
echo ==========================================
echo  Sent.
echo  https://github.com/Hendyoussef141/orglam-dashboard/actions
echo ==========================================

:end
echo.
pause
