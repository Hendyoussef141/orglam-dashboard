@echo off
REM ===================================================================
REM  Puts the worker back to an earlier version - which restores the
REM  variables a deploy wiped, because a Cloudflare version includes
REM  its bindings, not just the code.
REM
REM  Lives next to Orglam Worker.js and wrangler.toml.
REM ===================================================================

cd /d "%~dp0"

echo === Recent versions (newest first) ===
echo.
call npx wrangler deployments list
echo.
echo ============================================================
echo  Find the LAST version from BEFORE today's deploys - the one
echo  whose numbers were still working - and copy its Version ID.
echo ============================================================
echo.

set /p VER="Version ID to roll back to: "
if "%VER%"=="" (
  echo Nothing entered - nothing was changed.
  goto end
)

echo.
echo === Rolling back to %VER% ===
call npx wrangler rollback %VER%
if errorlevel 1 (
  echo.
  echo  The rollback failed - the reason is above. Nothing changed.
  goto end
)

echo.
echo ==========================================
echo  Rolled back. Check the app's numbers now.
echo ==========================================

:end
echo.
pause
