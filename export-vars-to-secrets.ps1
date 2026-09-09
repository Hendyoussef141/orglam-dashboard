# Run via export-vars-to-secrets.bat
# Reads the worker's own plain-text variables through the temporary /export-vars route and stores
# each one as a Cloudflare SECRET. Secrets survive every deploy; plain variables do not - which is
# what wiped them and zeroed the app when CI deployed the worker.

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$worker = 'https://dawn-king-6cc3.orglam-service.workers.dev'

if (-not (Test-Path 'wrangler.toml')) {
  Write-Host 'wrangler.toml is missing - run this from the orglam dashboard folder.' -ForegroundColor Red
  Read-Host 'Press Enter to close'; exit
}

$exportKey = if (Test-Path 'export-key.txt') { (Get-Content -Raw 'export-key.txt').Trim() } else { Read-Host 'EXPORT_KEY' }
if (-not $exportKey) { Write-Host 'No export key.'; Read-Host 'Press Enter'; exit }

Write-Host '=== Reading the values from your worker ==='
try {
  $vars = Invoke-RestMethod -Uri "$worker/export-vars?key=$exportKey" -Headers @{ 'Cache-Control' = 'no-store' }
} catch {
  Write-Host ''
  Write-Host 'The worker did not return the values.' -ForegroundColor Red
  Write-Host 'Check that EXPORT_KEY is set as a secret in Cloudflare AND that the worker with the'
  Write-Host '/export-vars route has actually been deployed.'
  Read-Host 'Press Enter to close'; exit
}

$names = @($vars.PSObject.Properties | ForEach-Object { $_.Name })
if ($names.Count -eq 0) {
  Write-Host 'The worker returned nothing - there are no plain-text variables left to convert.' -ForegroundColor Yellow
  Read-Host 'Press Enter to close'; exit
}
Write-Host ("    found " + $names.Count + ": " + ($names -join ', '))

Write-Host ''
Write-Host '============================================================'
Write-Host ' Paste a Cloudflare API token with "Edit Cloudflare Workers".'
Write-Host '   https://dash.cloudflare.com/profile/api-tokens'
Write-Host ' Nothing shows as you paste - that is normal.'
Write-Host '============================================================'
$tok = Read-Host -AsSecureString 'Cloudflare token'
$env:CLOUDFLARE_API_TOKEN = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
  [Runtime.InteropServices.Marshal]::SecureStringToBSTR($tok))
if (-not $env:CLOUDFLARE_API_TOKEN) { Write-Host 'No token entered.'; Read-Host 'Press Enter'; exit }

Write-Host ''
Write-Host '=== Saving them as secrets ==='
$ok = 0; $failed = @()
foreach ($p in $vars.PSObject.Properties) {
  Write-Host ('--- ' + $p.Name)
  $out = ($p.Value | npx --yes wrangler secret put $p.Name 2>&1)
  if ($LASTEXITCODE -eq 0) { Write-Host '    saved'; $ok++ }
  else {
    Write-Host '    FAILED:' -ForegroundColor Red
    $out | Select-Object -Last 4 | ForEach-Object { Write-Host ('      ' + $_) }
    $failed += $p.Name
  }
}
$env:CLOUDFLARE_API_TOKEN = ''

Write-Host ''
Write-Host ("$ok saved" + $(if ($failed.Count) { ', failed: ' + ($failed -join ', ') } else { '' }))
Write-Host ''
if ($failed.Count -eq 0) {
  Write-Host 'Done. Tell me, and I will remove the export route and switch the worker' -ForegroundColor Green
  Write-Host 'back to deploying automatically.' -ForegroundColor Green
}
Read-Host 'Press Enter to close'
