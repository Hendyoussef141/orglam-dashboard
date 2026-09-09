@echo off
REM ===================================================================
REM  READ-ONLY check. Deploys nothing, changes nothing.
REM
REM  Asks Cloudflare to list the live worker's variables, secrets and
REM  bindings using the same API the GitHub deploy uses. If this prints
REM  a list, the automated deploy will work. If it prints an error, the
REM  token is wrong and nothing has been risked.
REM
REM  Lives next to wrangler.toml.
REM ===================================================================

cd /d "%~dp0"

if not exist "wrangler.toml" (
  echo Could not find wrangler.toml in this folder.
  goto end
)

for /f "tokens=2 delims==" %%a in ('findstr /b "name" wrangler.toml') do set NAME=%%a
set NAME=%NAME: =%
set NAME=%NAME:"=%

echo Worker: %NAME%
echo.
set /p CF_TOKEN=Paste your Cloudflare API token: 
set /p CF_ACCOUNT=Paste your Cloudflare account id: 
echo.
echo === Asking Cloudflare what the live worker has ===

curl -sS "https://api.cloudflare.com/client/v4/accounts/%CF_ACCOUNT%/workers/scripts/%NAME%/settings" -H "Authorization: Bearer %CF_TOKEN%"

echo.
echo.
echo ==========================================
echo  If you see "success":true and a list of
echo  bindings above, the token is good.
echo.
echo  If you see "success":false, the token or
echo  the account id is wrong - nothing was
echo  changed either way.
echo ==========================================

set CF_TOKEN=
set CF_ACCOUNT=

:end
echo.
pause
