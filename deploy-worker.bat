@echo off
REM ===================================================================
REM  Deploys the WORKER only, by hand.
REM
REM  Put this in the folder that contains "orglam-app", next to
REM  Orglam Worker.js and wrangler.toml, and double-click it.
REM
REM  Deliberately manual: pushing the worker from GitHub Actions wiped
REM  its plain-text variables and zeroed every number in the app.
REM ===================================================================

cd /d "%~dp0"

if not exist "Orglam Worker.js" (
  echo Could not find "Orglam Worker.js" in this folder.
  echo Put this file next to it and run it again.
  goto end
)
if not exist "wrangler.toml" (
  echo Could not find wrangler.toml in this folder.
  goto end
)

findstr /c:"PASTE_YOUR_KV_NAMESPACE_ID" wrangler.toml >nul
if not errorlevel 1 (
  echo.
  echo  wrangler.toml still says PASTE_YOUR_KV_NAMESPACE_ID.
  echo  Deploying like this would point the worker's storage at nothing.
  echo.
  echo  Get the id here:
  echo    Cloudflare dashboard  ^-^>  Storage ^& Databases  ^-^>  KV
  echo    ^-^>  the namespace bound as SALES_CACHE  ^-^>  copy its ID
  echo.
  echo  Paste it into wrangler.toml in place of PASTE_YOUR_KV_NAMESPACE_ID,
  echo  then run this again. Nothing was deployed.
  goto end
)

echo === Checking the worker for syntax errors ===
REM wrangler expects the entrypoint named in wrangler.toml. A broken file must fail HERE, not
REM half-way through a deploy that leaves the live worker in pieces.
copy /y "Orglam Worker.js" worker.js >nul
REM Checked as .mjs: the worker is an ES module ("export default"), and node reads a .js file as
REM CommonJS, where that line is itself a syntax error - so checking worker.js would refuse to
REM deploy a perfectly good worker every time.
copy /y "Orglam Worker.js" worker-check.mjs >nul
node --check worker-check.mjs
if errorlevel 1 (
  echo.
  echo  The worker has a syntax error - see above. NOTHING was deployed.
  del worker.js worker-check.mjs >nul 2>&1
  goto end
)
del worker-check.mjs >nul 2>&1
echo ok
echo.

echo === Deploying to Cloudflare ===
REM keep_vars in wrangler.toml is what stops this replacing the dashboard-set variables.
call npx wrangler deploy
if errorlevel 1 (
  echo.
  echo  The deploy failed - the reason is above. The old worker is still live.
  echo.
  echo  If it asked you to log in, run:  npx wrangler login
  del worker.js >nul 2>&1
  goto end
)

del worker.js >nul 2>&1
echo.
echo ==========================================
echo  Worker deployed.
echo ==========================================

:end
echo.
pause
