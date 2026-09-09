@echo off
REM ===================================================================
REM  Undoes the blocked commit, removes the files that hold live keys
REM  (GitHub push protection rejects them, correctly), and pushes.
REM
REM  Double-click this. It stays open so you can read what happened.
REM ===================================================================

cd /d "%~dp0"

if not exist ".git" (
  echo This is not the project folder - put this file next to deploy.bat.
  goto end
)

echo === Undoing the blocked commit (files are kept) ===
git reset HEAD~1
echo.

echo === Removing the key-bearing helper files ===
del /f /q set-worker-vars.bat >nul 2>&1
del /f /q finish-shopify-token.bat >nul 2>&1
del /f /q test-shopify-token.bat >nul 2>&1
echo done
echo.

echo === Making sure git never picks them up again ===
findstr /x /c:"set-worker-vars.bat" .gitignore >nul 2>&1 || echo set-worker-vars.bat>> .gitignore
findstr /x /c:"finish-shopify-token.bat" .gitignore >nul 2>&1 || echo finish-shopify-token.bat>> .gitignore
findstr /x /c:"test-shopify-token.bat" .gitignore >nul 2>&1 || echo test-shopify-token.bat>> .gitignore
findstr /x /c:"set-secrets.bat" .gitignore >nul 2>&1 || echo set-secrets.bat>> .gitignore
echo done
echo.

echo === What will be sent ===
git add -A
git status --short
echo.

git diff --cached --quiet
if not errorlevel 1 (
  echo Nothing to send.
  goto end
)

git commit -m "Worker deploys removed from CI"
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
echo  Sent.
echo  https://github.com/Hendyoussef141/orglam-dashboard/actions
echo ==========================================

:end
echo.
pause
