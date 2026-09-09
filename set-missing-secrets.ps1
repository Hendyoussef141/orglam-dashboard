# Run via set-missing-secrets.bat
# Asks for the nine values that a deploy keeps wiping and stores them as Cloudflare SECRETS.
# Secrets survive every deploy; plain-text variables do not - that is the whole point.

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

if (-not (Test-Path 'wrangler.toml')) {
  Write-Host 'wrangler.toml is missing - run this from the orglam dashboard folder.' -ForegroundColor Red
  Read-Host 'Press Enter to close'; exit
}

# Only the ones that were plain-text variables and got wiped. Everything else is already a secret.
$names = @(
  @{ n = 'SHOPIFY_ACCESS_TOKEN';           d = 'Shopify admin API token - without it every sales number is zero' },
  @{ n = 'FIREBASE_SERVICE_ACCOUNT_JSON';  d = 'The whole Firebase service-account JSON (push notifications)' },
  @{ n = 'LWA_CLIENT_ID';                  d = 'Amazon SP-API client id' },
  @{ n = 'NOON_KEY_ID';                    d = 'noon key id' },
  @{ n = 'NOON_PARTNER_ID';                d = 'noon partner id' },
  @{ n = 'NOON_PROJECT_CODE';              d = 'noon project code' },
  @{ n = 'GMAIL_CLIENT_ID';                d = 'Gmail OAuth client id' },
  @{ n = 'GMAIL_REFRESH_TOKEN';            d = 'Gmail refresh token' },
  @{ n = 'GEMINI_API_KEY';                 d = 'Gemini key (Elora)' }
)

Write-Host '============================================================'
Write-Host ' Paste a Cloudflare API token with "Edit Cloudflare Workers".'
Write-Host '   https://dash.cloudflare.com/profile/api-tokens'
Write-Host ' Nothing shows as you paste - that is normal.'
Write-Host '============================================================'
Write-Host ''
$tok = Read-Host -AsSecureString 'Cloudflare token'
$env:CLOUDFLARE_API_TOKEN = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
  [Runtime.InteropServices.Marshal]::SecureStringToBSTR($tok))
if (-not $env:CLOUDFLARE_API_TOKEN) { Write-Host 'No token entered.'; Read-Host 'Press Enter'; exit }

Write-Host ''
Write-Host 'Now the nine values. Copy each from the worker Settings page'
Write-Host '(Variables and Secrets - the plain-text ones show their value).'
Write-Host 'Press Enter on any you do not have, and it is left alone.'
Write-Host ''

$ok = 0; $skipped = 0; $failed = @()
foreach ($item in $names) {
  Write-Host ''
  Write-Host ('--- ' + $item.n) -ForegroundColor Cyan
  Write-Host ('    ' + $item.d)
  $val = Read-Host '    value'
  if ([string]::IsNullOrWhiteSpace($val)) { Write-Host '    skipped'; $skipped++; continue }
  $out = ($val | npx --yes wrangler secret put $item.n 2>&1)
  if ($LASTEXITCODE -eq 0) { Write-Host '    saved as a secret' -ForegroundColor Green; $ok++ }
  else {
    Write-Host '    FAILED:' -ForegroundColor Red
    $out | Select-Object -Last 4 | ForEach-Object { Write-Host ('      ' + $_) }
    $failed += $item.n
  }
}

$env:CLOUDFLARE_API_TOKEN = ''
Write-Host ''
Write-Host ("$ok saved, $skipped skipped" + ($(if ($failed.Count) { ', failed: ' + ($failed -join ', ') } else { '' })))
Write-Host ''
if ($ok -gt 0) {
  Write-Host 'Next: run deploy.bat. These values now survive every deploy.' -ForegroundColor Green
}
Read-Host 'Press Enter to close'
