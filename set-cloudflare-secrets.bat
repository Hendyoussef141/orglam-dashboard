@echo off
REM ===================================================================
REM  Moves your API keys out of the code and into Cloudflare.
REM  Run ONCE, then delete cloudflare-secrets.json.
REM
REM  Uses an API token instead of the browser login, which fails on
REM  some machines ("Wrangler login failed").
REM ===================================================================

cd /d "%~dp0"

if not exist "cloudflare-secrets.json" (
  echo cloudflare-secrets.json is missing - put it next to this file.
  goto end
)
if not exist "wrangler.toml" (
  echo wrangler.toml is missing - wrangler needs it to know which worker to update.
  goto end
)

findstr /x /c:"cloudflare-secrets.json" .gitignore >nul 2>&1
if errorlevel 1 >> .gitignore echo cloudflare-secrets.json

echo ============================================================
echo  Paste a Cloudflare API token with "Edit Cloudflare Workers"
echo  permission.
echo.
echo  Make one here (or reuse the one you gave GitHub):
echo    https://dash.cloudflare.com/profile/api-tokens
echo    Create Token  -^>  "Edit Cloudflare Workers" template
echo    -^>  Account Resources: pick your account  -^>  Create
echo.
echo  Nothing is shown as you paste - that is normal.
echo ============================================================
echo.

REM Read without echoing it to the screen.
for /f "delims=" %%t in ('powershell -NoProfile -Command "$s=Read-Host -AsSecureString 'Token'; [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))"') do set CLOUDFLARE_API_TOKEN=%%t

if "%CLOUDFLARE_API_TOKEN%"=="" (
  echo No token entered - nothing was changed.
  goto end
)

echo.
echo === Checking the token ===
call npx --yes wrangler whoami
if errorlevel 1 (
  echo.
  echo  That token was rejected. Check it has "Edit Cloudflare Workers"
  echo  and that an account was selected under Account Resources.
  goto end
)

echo.
echo === Setting the secrets ===
echo.
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$j = Get-Content -Raw 'cloudflare-secrets.json' | ConvertFrom-Json; $ok=0; $fail=0; foreach ($p in $j.PSObject.Properties) { Write-Host ('--- ' + $p.Name); $out = ($p.Value | npx --yes wrangler secret put $p.Name 2>&1); if ($LASTEXITCODE -eq 0) { Write-Host '    ok' } else { Write-Host '    FAILED:'; $out | Select-Object -Last 6 | ForEach-Object { Write-Host ('      ' + $_) }; $fail++ } }; Write-Host ''; Write-Host ($ok.ToString() + ' set, ' + $fail.ToString() + ' failed'); if ($fail -gt 0) { exit 1 }"

set CLOUDFLARE_API_TOKEN=

if errorlevel 1 (
  echo.
  echo  Send me the FAILED text above. Nothing is broken - the live
  echo  worker is untouched.
  goto end
)

echo.
echo ==========================================
echo  Done. Delete cloudflare-secrets.json now,
echo  then run deploy.bat
echo ==========================================

:end
echo.
pause
