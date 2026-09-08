@echo off
REM ===================================================================
REM  One-time repair. The first attempt committed the worker while it
REM  still had the API keys in it; GitHub blocks the PUSH because that
REM  old commit is still in the history, even though the file is now
REM  clean. This collapses the local commits into one clean commit.
REM
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
echo === Collapsing the local commits ===
REM 6bfae3a is the commit already on GitHub. Everything after it is rewound to staged changes,
REM then committed once - so the commit that contained the keys stops existing.
git reset --soft 6bfae3a
if errorlevel 1 (
  echo Could not rewind. Send me this output.
  goto end
)

git add -A
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
echo ==========================================

:end
echo.
pause
