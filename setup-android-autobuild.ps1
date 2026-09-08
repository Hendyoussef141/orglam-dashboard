# Run via setup-android-autobuild.bat (which just launches this).
# 1. Teaches the Android app to sign itself when built on GitHub.
# 2. Exports the key the installed app was signed with, so an update installs over it.

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$gradle = 'orglam-app\android\app\build.gradle'
$ks = Join-Path $env:USERPROFILE '.android\debug.keystore'

if (-not (Test-Path $gradle)) {
  Write-Host "Could not find $gradle" -ForegroundColor Red
  Write-Host 'Run this from the folder that contains "orglam-app".'
  Read-Host 'Press Enter to close'; exit
}
if (-not (Test-Path $ks)) {
  Write-Host ''
  Write-Host 'Could not find the key your app was signed with:' -ForegroundColor Red
  Write-Host "  $ks"
  Write-Host ''
  Write-Host 'That file is what lets an update install over the existing app.'
  Write-Host 'Tell me, and I will set up a brand-new key instead - but then'
  Write-Host 'everyone has to uninstall and reinstall the app once.'
  Read-Host 'Press Enter to close'; exit
}

Write-Host '=== 1/3  Teaching the app to sign itself ==='
$c = Get-Content -Raw $gradle
if ($c -match 'signingConfigs') {
  Write-Host '    already set up'
} else {
  # Falls back to unsigned when the key file is absent, so a plain local build on this PC keeps
  # behaving exactly as it does now.
  $block = @"
    // Signing: GitHub restores the keystore next to this file and passes the passwords in as
    // environment variables. With no key file present the release build is left unsigned, as before.
    def orglamKeystore = file(System.getenv("ORGLAM_KEYSTORE_PATH") ?: "release.jks")
    signingConfigs {
        release {
            if (orglamKeystore.exists()) {
                storeFile orglamKeystore
                storePassword System.getenv("ORGLAM_STORE_PASSWORD") ?: "android"
                keyAlias System.getenv("ORGLAM_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword System.getenv("ORGLAM_KEY_PASSWORD") ?: "android"
            }
        }
    }
    buildTypes {
        release {
            if (orglamKeystore.exists()) signingConfig signingConfigs.release
"@
  $pattern = "(?m)^    buildTypes \{\r?\n        release \{"
  if ($c -notmatch $pattern) {
    Write-Host '    Could not find the buildTypes block - send me build.gradle and I will patch it.' -ForegroundColor Red
    Read-Host 'Press Enter to close'; exit
  }
  $c = [regex]::Replace($c, $pattern, $block.TrimEnd("`r","`n"), 1)
  Copy-Item $gradle "$gradle.backup" -Force
  Set-Content -NoNewline -Path $gradle -Value $c
  Write-Host '    done (a .backup copy was kept)'
}

Write-Host ''
Write-Host '=== 2/3  Exporting your signing key ==='
[Convert]::ToBase64String([IO.File]::ReadAllBytes($ks)) | Set-Content -NoNewline 'android-keystore-base64.txt'
if (-not (Select-String -Path '.gitignore' -SimpleMatch 'android-keystore-base64.txt' -Quiet)) {
  Add-Content '.gitignore' 'android-keystore-base64.txt'
}
Write-Host '    written to android-keystore-base64.txt'

Write-Host ''
Write-Host '=== 3/3  What to add on GitHub ==='
Write-Host ''
Write-Host '  https://github.com/Hendyoussef141/orglam-dashboard/settings/secrets/actions'
Write-Host ''
Write-Host '  Four secrets:'
Write-Host '    ANDROID_KEYSTORE_BASE64  = all the text inside android-keystore-base64.txt'
Write-Host '    ANDROID_STORE_PASSWORD   = android'
Write-Host '    ANDROID_KEY_ALIAS        = androiddebugkey'
Write-Host '    ANDROID_KEY_PASSWORD     = android'
Write-Host ''
Write-Host '  Then the "Variables" tab on the same page:'
Write-Host '    BUILD_ANDROID = true'
Write-Host ''
Write-Host '  Then run deploy.bat. Every push after that builds the app and'
Write-Host '  publishes it to every phone by itself.'
Write-Host ''
Write-Host '  Delete android-keystore-base64.txt once the secret is saved.'
Write-Host ''
$open = Read-Host 'Open the file and the GitHub page now? (y/n)'
if ($open -eq 'y') {
  Start-Process notepad.exe 'android-keystore-base64.txt'
  Start-Process 'https://github.com/Hendyoussef141/orglam-dashboard/settings/secrets/actions'
}
Read-Host 'Press Enter to close'
