@echo off
REM ===================================================================
REM  One-time repair, take two. The remote has moved on, and the local
REM  history still contains the commit that held the API keys - GitHub
REM  blocks the push because of that old commit even though the file is
REM  now clean.
REM
REM  This rebuilds your local commits as ONE clean commit sitting
REM  directly on top of whatever is on GitHub right now.
REM  Your files are NOT touched - only the history of them.
REM ===================================================================

cd /d "%~dp0"

if not exist ".git" (
  echo No repository here.
  goto end
)

echo === Ignoring wrangler's local cache ===
findstr /x /c:".wrangler/" .gitignore >nul 2>&1
if errorlevel 1 >> .gitignore echo .wrangler/
git rm -r --cached .wrangler >nul 2>&1

echo.
echo === Reading what is on GitHub ===
git fetch origin
if errorlevel 1 (
  echo Could not reach GitHub. Send me this output.
  goto end
)
git log --oneline -1 origin/main

echo.
echo === Rebuilding as one clean commit on top of it ===
REM --soft keeps every file exactly as it is on disk and simply re-points the branch, so the old
REM commits (including the one with the keys) stop being part of what gets pushed.
git reset --soft origin/main
if errorlevel 1 (
  echo Could not rewind. Send me this output.
  goto end
)

git add -A
git diff --cached --quiet
if not errorlevel 1 (
  echo Nothing differs from GitHub - already up to date.
  goto end
)

git commit -m "Auto-deploy setup; API keys moved to Cloudflare secrets"
git rev-parse --verify HEAD >nul 2>&1
if errorlevel 1 (
  echo The commit failed - see above.
  goto end
)

echo.
echo === What is being sent ===
git log --oneline -2
echo.

echo === Sending to GitHub ===
git push origin main
if errorlevel 1 (
  echo.
  echo  Still refused - send me the text above.
  goto end
)

echo.
echo ==========================================
echo  Sent. The deploy is running:
echo  https://github.com/Hendyoussef141/orglam-dashboard/actions
echo.
echo  From now on just use deploy.bat
echo ==========================================

:end
echo.
pause
